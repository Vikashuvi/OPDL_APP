/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
*/

package org.opdl.transfer.Backends.LanBackend;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.opdl.transfer.Backends.BaseLink;
import org.opdl.transfer.Backends.BaseLinkProvider;
import org.opdl.transfer.Device;
import org.opdl.transfer.DeviceInfo;
import org.opdl.transfer.Helpers.OpdlKernelBridge;
import org.opdl.transfer.Helpers.SecurityHelpers.SslHelper;
import org.opdl.transfer.Helpers.ThreadHelper;
import org.opdl.transfer.NetworkPacket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.NotYetConnectedException;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;

import kotlin.text.Charsets;

public class LanLink extends BaseLink {
    private static final int PAYLOAD_IO_BUFFER_SIZE = 64 * 1024;
    private static final int PAYLOAD_SOCKET_BUFFER_SIZE = 256 * 1024;

    public enum ConnectionStarted {
        Locally, Remotely
    }

    private DeviceInfo deviceInfo;

    private volatile Socket socket = null;

    @Override
    public void disconnect() {
        Log.i("LanLink/Disconnect", "socket:" + (socket != null ? socket.hashCode() : "null"));
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            Log.e("LanLink", "Error", e);
        }
    }

    // Returns the old socket
    @WorkerThread
    public Socket reset(final Socket newSocket, final DeviceInfo deviceInfo) throws IOException {

        this.deviceInfo = deviceInfo;

        Socket oldSocket = socket;
        socket = newSocket;

        if (oldSocket != null) {
            try {
                oldSocket.close();
            } catch (Exception ignored) {
            }
        }

        // Create a thread to take care of incoming data for the new socket
        ThreadHelper.execute(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(newSocket.getInputStream(), Charsets.UTF_8));
                while (true) {
                    String packet;
                    try {
                        packet = reader.readLine();
                    } catch (SocketTimeoutException e) {
                        continue;
                    }
                    if (packet == null) {
                        throw new IOException("End of stream");
                    }
                    if (packet.isEmpty()) {
                        continue;
                    }
                    NetworkPacket np = NetworkPacket.unserialize(packet);
                    receivedNetworkPacket(np);
                }
            } catch (Exception e) {
                Log.i("LanLink", "Socket closed: " + newSocket.hashCode() + ". Reason: " + e.getMessage());
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                } // Wait a bit because we might receive a new socket meanwhile
                boolean thereIsaANewSocket = (newSocket != socket);
                if (!thereIsaANewSocket) {
                    Log.i("LanLink", "Socket closed and there's no new socket, disconnecting device");
                    getLinkProvider().onConnectionLost(LanLink.this);
                }
            }
        });

        return oldSocket;
    }

    @WorkerThread
    public LanLink(@NonNull Context context, @NonNull DeviceInfo deviceInfo, @NonNull BaseLinkProvider linkProvider,
            @NonNull Socket socket) throws IOException {
        super(context, linkProvider);
        reset(socket, deviceInfo);
    }

    @Override
    public String getName() {
        return "LanLink";
    }

    @Override
    public DeviceInfo getDeviceInfo() {
        return deviceInfo;
    }

    @WorkerThread
    @Override
    public boolean sendPacket(@NonNull NetworkPacket np, @NonNull final Device.SendPacketStatusCallback callback,
            boolean sendPayloadFromSameThread) {
        if (socket == null) {
            Log.e("OPDL/sendPacket", "Not yet connected");
            callback.onFailure(new NotYetConnectedException());
            return false;
        }

        try {
            final boolean canAttemptOpdlFastPath = np.hasPayload() && OpdlKernelBridge.canAttemptFastPath(deviceInfo);

            // Prepare socket for the payload
            final ServerSocket server;
            if (np.hasPayload()) {
                server = LanLinkProvider.openServerSocketOnFreePort(LanLinkProvider.PAYLOAD_TRANSFER_MIN_PORT);
                JSONObject payloadTransferInfo = new JSONObject();
                payloadTransferInfo.put("port", server.getLocalPort());
                if (canAttemptOpdlFastPath) {
                    payloadTransferInfo.put(OpdlKernelBridge.PAYLOAD_TRANSFER_MODE_KEY,
                            OpdlKernelBridge.PAYLOAD_TRANSFER_MODE_FAST_PATH);
                }
                np.setPayloadTransferInfo(payloadTransferInfo);
            } else {
                server = null;
            }

            // Log.e("LanLink/sendPacket", np.getType());

            // Send body of the network packet
            try {
                OutputStream writer = socket.getOutputStream();
                writer.write(np.serialize().getBytes(Charsets.UTF_8));
                writer.flush();
            } catch (Exception e) {
                disconnect(); // main socket is broken, disconnect
                if (server != null) {
                    try {
                        server.close();
                    } catch (Exception ignored) {
                    }
                }
                throw e;
            }

            // Send payload
            if (server != null) {
                if (canAttemptOpdlFastPath
                        && OpdlKernelBridge.trySendPayloadViaKernel(getDeviceId(), np.getPayload())) {
                    callback.onPayloadProgressChanged(100);
                } else if (sendPayloadFromSameThread) {
                    sendPayload(np, callback, server);
                } else {
                    ThreadHelper.execute(() -> {
                        try {
                            sendPayload(np, callback, server);
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e("LanLink/sendPacket", "Async sendPayload failed for packet of type " + np.getType()
                                    + ". The Plugin was NOT notified.");
                        }
                    });
                }
            }

            if (!np.isCanceled()) {
                callback.onSuccess();
            }
            return true;
        } catch (Exception e) {
            callback.onFailure(e);
            return false;
        } finally {
            // Make sure we close the payload stream, if any
            if (np.hasPayload()) {
                np.getPayload().close();
            }
        }
    }

    private void sendPayload(NetworkPacket np, Device.SendPacketStatusCallback callback, ServerSocket server)
            throws IOException {
        Socket payloadSocket = null;
        OutputStream outputStream = null;
        InputStream inputStream;
        try {
            if (!np.isCanceled()) {
                // Wait a maximum of 10 seconds for the other end to establish a connection with
                // our socket, close it afterwards
                server.setSoTimeout(10 * 1000);

                payloadSocket = server.accept();

                // Convert to SSL if needed
                if (!deviceInfo.supportsOpdlFastPath) {
                    payloadSocket = SslHelper.convertToSslSocket(context, payloadSocket, getDeviceId(), true, false);
                }
                payloadSocket.setTcpNoDelay(true);
                payloadSocket.setSendBufferSize(PAYLOAD_SOCKET_BUFFER_SIZE);
                payloadSocket.setReceiveBufferSize(PAYLOAD_SOCKET_BUFFER_SIZE);

                outputStream = payloadSocket.getOutputStream();
                inputStream = np.getPayload().getInputStream();

                Log.i("OPDL/LanLink", "Beginning to send payload for " + np.getType());
                byte[] buffer = new byte[PAYLOAD_IO_BUFFER_SIZE];
                int bytesRead;
                long size = np.getPayloadSize();
                long progress = 0;
                long timeSinceLastUpdate = -1;
                while (!np.isCanceled() && (bytesRead = inputStream.read(buffer)) != -1) {
                    // Log.e("ok",""+bytesRead);
                    progress += bytesRead;
                    outputStream.write(buffer, 0, bytesRead);
                    if (size > 0) {
                        if (timeSinceLastUpdate + 500 < System.currentTimeMillis()) { // Report progress every half a
                                                                                      // second
                            long percent = ((100 * progress) / size);
                            callback.onPayloadProgressChanged((int) percent);
                            timeSinceLastUpdate = System.currentTimeMillis();
                        }
                    }
                }
                outputStream.flush();
                Log.i("OPDL/LanLink", "Finished sending payload (" + progress + " bytes written)");
            }
        } catch (SocketTimeoutException e) {
            Log.e("LanLink", "Socket for payload in packet " + np.getType()
                    + " timed out. The other end didn't fetch the payload.");
        } catch (SSLHandshakeException e) {
            // The exception can be due to several causes. "Connection closed by peer" seems
            // to be a common one.
            // If we could distinguish different cases we could react differently for some
            // of them, but I haven't found how.
            Log.e("sendPacket", "Payload SSLSocket failed");
            e.printStackTrace();
        } finally {
            try {
                server.close();
            } catch (Exception ignored) {
            }
            try {
                IOUtils.close(payloadSocket);
            } catch (Exception ignored) {
            }
            np.getPayload().close();
            try {
                IOUtils.close(outputStream);
            } catch (Exception ignored) {
            }
        }
    }

    private void receivedNetworkPacket(NetworkPacket np) {

        if (np.hasPayloadTransferInfo()) {
            String transferMode = np.getPayloadTransferInfo().optString(OpdlKernelBridge.PAYLOAD_TRANSFER_MODE_KEY, "");
            if (OpdlKernelBridge.PAYLOAD_TRANSFER_MODE_FAST_PATH.equals(transferMode)) {
                NetworkPacket.Payload payload = OpdlKernelBridge.tryReceivePayloadViaKernel(getDeviceId(),
                        np.getPayloadSize());
                if (payload != null) {
                    np.setPayload(payload);
                    packetReceived(np);
                    return;
                }
            }

            Socket payloadSocket = new Socket();
            try {
                int tcpPort = np.getPayloadTransferInfo().getInt("port");
                InetSocketAddress deviceAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
                payloadSocket.connect(new InetSocketAddress(deviceAddress.getAddress(), tcpPort));
                if (!deviceInfo.supportsOpdlFastPath) {
                    payloadSocket = SslHelper.convertToSslSocket(context, payloadSocket, getDeviceId(), true, true);
                }
                np.setPayload(new NetworkPacket.Payload(payloadSocket, np.getPayloadSize()));
            } catch (Exception e) {
                try {
                    payloadSocket.close();
                } catch (Exception ignored) {
                }
                Log.e("OPDL/LanLink", "Exception connecting to payload remote socket", e);
            }

        }

        packetReceived(np);
    }

}
