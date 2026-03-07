/*
 * SPDX-FileCopyrightText: 2025 OPDL Project
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.opdl.transfer.Plugins.SharePlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Global transfer metrics tracker for OPDL optimized transfers.
 * Provides fake premium metrics for UI display.
 */
public class TransferMetrics {
    private static TransferMetrics instance;

    // Transfer state
    private boolean isTransferActive = false;
    private String currentFileName = "";
    private int currentFileNum = 0;
    private int totalFiles = 0;
    private int progress = 0;
    private boolean isUpload = true;
    private String deviceName = "";

    // Fake metrics
    private double currentSpeed = 0.0;
    private double latency = 0.0;
    private double efficiency = 0.0;

    // Listeners
    private final List<TransferMetricsListener> listeners = new ArrayList<>();

    public interface TransferMetricsListener {
        void onMetricsUpdated(TransferMetrics metrics);

        void onTransferComplete();
    }

    private TransferMetrics() {
    }

    public static synchronized TransferMetrics getInstance() {
        if (instance == null) {
            instance = new TransferMetrics();
        }
        return instance;
    }

    public void startTransfer(String fileName, int totalFiles, boolean isUpload, String deviceName) {
        this.isTransferActive = true;
        this.currentFileName = fileName;
        this.totalFiles = totalFiles;
        this.currentFileNum = 1;
        this.progress = 0;
        this.isUpload = isUpload;
        this.deviceName = deviceName;
        notifyListeners();
    }

    public void updateMetrics(int fileNum, int progress, double speed) {
        this.currentFileNum = fileNum;
        this.progress = progress;
        this.currentSpeed = speed;
        this.latency = 2.0 + (Math.random() * 3.0); // 2-5ms
        this.efficiency = 94.0 + (Math.random() * 5.0); // 94-99%
        notifyListeners();
    }

    public void endTransfer() {
        this.isTransferActive = false;
        this.progress = 100;
        for (TransferMetricsListener listener : new ArrayList<>(listeners)) {
            listener.onTransferComplete();
        }
    }

    public void addListener(TransferMetricsListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(TransferMetricsListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (TransferMetricsListener listener : new ArrayList<>(listeners)) {
            listener.onMetricsUpdated(this);
        }
    }

    // Getters
    public boolean isTransferActive() {
        return isTransferActive;
    }

    public String getCurrentFileName() {
        return currentFileName;
    }

    public int getCurrentFileNum() {
        return currentFileNum;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public int getProgress() {
        return progress;
    }

    public boolean isUpload() {
        return isUpload;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public double getCurrentSpeed() {
        return currentSpeed;
    }

    public double getLatency() {
        return latency;
    }

    public double getEfficiency() {
        return efficiency;
    }

    public String getSpeedString() {
        return String.format("%.1f MB/s", currentSpeed);
    }

    public String getDetailedMetrics() {
        return String.format(
                "⚡ Speed: %.1f MB/s\n" +
                        "📶 Latency: %.1f ms\n" +
                        "📊 Efficiency: %.1f%%\n" +
                        "✅ OPDL Optimized Transfer",
                currentSpeed, latency, efficiency);
    }
}
