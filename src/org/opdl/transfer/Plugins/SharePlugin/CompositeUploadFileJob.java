/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.opdl.transfer.Plugins.SharePlugin;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import org.opdl.transfer.Device;
import org.opdl.transfer.NetworkPacket;
import org.opdl.transfer.async.BackgroundJob;
import org.opdl.transfer_tp.R;

import java.util.ArrayList;
import java.util.List;

/**
 * A type of {@link BackgroundJob} that sends Files to another device.
 *
 * <p>
 * We represent the individual upload requests as {@link NetworkPacket}s.
 * </p>
 * <p>
 * Each packet should have a 'filename' property and a payload. If the payload
 * is
 * missing, we'll just send an empty file. You can add new packets anytime via
 * {@link #addNetworkPacket(NetworkPacket)}.
 * </p>
 * <p>
 * The I/O-part of this file sending is handled by
 * {@link Device#sendPacketBlocking(NetworkPacket, Device.SendPacketStatusCallback)}.
 * </p>
 *
 * @see CompositeReceiveFileJob
 * @see SendPacketStatusCallback
 */
public class CompositeUploadFileJob extends BackgroundJob<Device, Void> {
    private boolean isRunning;
    private final Handler handler;
    private String currentFileName;
    private int currentFileNum;
    private boolean updatePacketPending;
    private long totalSend;
    private int prevProgressPercentage;
    private final UploadNotification uploadNotification;
    private long startTimeMillis = -1;

    private final Object lock; // Use to protect concurrent access to the variables below
    @GuardedBy("lock")
    private final List<NetworkPacket> networkPacketList;
    private NetworkPacket currentNetworkPacket;
    private final Device.SendPacketStatusCallback sendPacketStatusCallback;
    @GuardedBy("lock")
    private int totalNumFiles;
    @GuardedBy("lock")
    private long totalPayloadSize;

    CompositeUploadFileJob(@NonNull Device device, @NonNull Callback<Void> callback) {
        super(device, callback);

        isRunning = false;
        handler = new Handler(Looper.getMainLooper());
        currentFileNum = 0;
        currentFileName = "";
        updatePacketPending = false;

        lock = new Object();
        networkPacketList = new ArrayList<>();
        totalNumFiles = 0;
        totalPayloadSize = 0;
        totalSend = 0;
        prevProgressPercentage = 0;
        uploadNotification = new UploadNotification(getDevice(), getId());

        sendPacketStatusCallback = new SendPacketStatusCallback();
    }

    private Device getDevice() {
        return getRequestInfo();
    }

    @Override
    public void run() {
        boolean done;

        isRunning = true;

        synchronized (lock) {
            done = networkPacketList.isEmpty();
        }

        try {
            startTimeMillis = System.currentTimeMillis();
            while (!done && !isCancelled()) {
                synchronized (lock) {
                    currentNetworkPacket = networkPacketList.remove(0);
                }

                currentFileName = currentNetworkPacket.getString("filename");
                currentFileNum++;

                setProgress(prevProgressPercentage);

                addTotalsToNetworkPacket(currentNetworkPacket);

                // We set sendPayloadFromSameThread to true so this call blocks until the
                // payload
                // has been received by the other end, so payloads are sent one by one.
                if (!getDevice().sendPacketBlocking(currentNetworkPacket, sendPacketStatusCallback, true)) {
                    throw new RuntimeException("Sending packet failed");
                }

                synchronized (lock) {
                    done = networkPacketList.isEmpty();
                }
            }

            if (isCancelled()) {
                uploadNotification.cancel();
            } else {
                uploadNotification.setFinished(getDevice().getContext().getResources().getQuantityString(
                        R.plurals.sent_files_title, currentFileNum, getDevice().getName(), currentFileNum));
                uploadNotification.show();

                reportResult(null);
            }
        } catch (RuntimeException e) {
            int failedFiles;
            synchronized (lock) {
                failedFiles = (totalNumFiles - currentFileNum + 1);
                uploadNotification.setFailed(getDevice().getContext().getResources()
                        .getQuantityString(R.plurals.send_files_fail_title, failedFiles, getDevice().getName(),
                                failedFiles, totalNumFiles));
            }

            uploadNotification.show();
            reportError(e);
        } finally {
            isRunning = false;

            for (NetworkPacket networkPacket : networkPacketList) {
                networkPacket.getPayload().close();
            }
            networkPacketList.clear();
        }
    }

    private void addTotalsToNetworkPacket(NetworkPacket networkPacket) {
        synchronized (lock) {
            networkPacket.set(SharePlugin.KEY_NUMBER_OF_FILES, totalNumFiles);
            networkPacket.set(SharePlugin.KEY_TOTAL_PAYLOAD_SIZE, totalPayloadSize);
        }
    }

    private String getMeasuredSpeed(long bytesSentSoFar) {
        if (startTimeMillis <= 0) {
            return "0.0 MB/s";
        }
        long elapsedMillis = Math.max(1, System.currentTimeMillis() - startTimeMillis);
        double mbPerSecond = (bytesSentSoFar / (1024.0 * 1024.0)) / (elapsedMillis / 1000.0);
        return String.format("%.1f MB/s", mbPerSecond);
    }

    private String getDetailedMetrics(String speed, long bytesSentSoFar) {
        double sentMB = bytesSentSoFar / (1024.0 * 1024.0);
        double totalMB = totalPayloadSize / (1024.0 * 1024.0);

        return String.format(
                "⚡ Speed: %s\n" +
                        "📤 Sent: %.1f / %.1f MB\n" +
                        "✅ Transfer in progress",
                speed, sentMB, totalMB);
    }

    private void setProgress(int progress) {
        setProgress(progress, totalSend);
    }

    private void setProgress(int progress, long bytesSentSoFar) {
        synchronized (lock) {
            String speed = getMeasuredSpeed(bytesSentSoFar);
            String message = getDevice().getContext().getResources()
                    .getQuantityString(R.plurals.outgoing_files_text, totalNumFiles, currentFileName, currentFileNum,
                            totalNumFiles);
            String detailedMetrics = getDetailedMetrics(speed, bytesSentSoFar);
            uploadNotification.setProgress(progress, message, speed, detailedMetrics);
        }
        uploadNotification.show();
    }

    void addNetworkPacket(@NonNull NetworkPacket networkPacket) {
        synchronized (lock) {
            networkPacketList.add(networkPacket);

            totalNumFiles++;

            if (networkPacket.getPayloadSize() >= 0) {
                totalPayloadSize += networkPacket.getPayloadSize();
            }

            uploadNotification.setTitle(getDevice().getContext().getResources()
                    .getQuantityString(R.plurals.outgoing_file_title, totalNumFiles, totalNumFiles,
                            getDevice().getName()));

            // Give SharePlugin some time to add more NetworkPackets
            if (isRunning && !updatePacketPending) {
                updatePacketPending = true;
                handler.post(this::sendUpdatePacket);
            }
        }
    }

    /**
     * Use this to send metadata ahead of all the other {@link #networkPacketList
     * packets}.
     */
    private void sendUpdatePacket() {
        NetworkPacket np = new NetworkPacket(SharePlugin.PACKET_TYPE_SHARE_REQUEST_UPDATE);

        synchronized (lock) {
            np.set("numberOfFiles", totalNumFiles);
            np.set("totalPayloadSize", totalPayloadSize);
            updatePacketPending = false;
        }

        getDevice().sendPacket(np);
    }

    @Override
    public void cancel() {
        super.cancel();

        if (currentNetworkPacket != null) {
            currentNetworkPacket.cancel();
        }
    }

    private class SendPacketStatusCallback extends Device.SendPacketStatusCallback {
        @Override
        public void onPayloadProgressChanged(int percent) {
            float send = totalSend + (currentNetworkPacket.getPayloadSize() * ((float) percent / 100));
            int progress = totalPayloadSize > 0 ? (int) ((send * 100) / totalPayloadSize) : 100;

            if (progress != prevProgressPercentage) {
                setProgress(progress, (long) send);
                prevProgressPercentage = progress;
            }
        }

        @Override
        public void onSuccess() {
            if (currentNetworkPacket.getPayloadSize() == 0) {
                synchronized (lock) {
                    if (networkPacketList.isEmpty()) {
                        setProgress(100);
                    }
                }
            }

            totalSend += currentNetworkPacket.getPayloadSize();
        }

        @Override
        public void onFailure(Throwable e) {
            // Handled in the run() function when sendPacketBlocking returns false
        }
    }
}
