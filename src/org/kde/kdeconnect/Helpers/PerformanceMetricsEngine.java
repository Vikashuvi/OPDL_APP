/*
 * SPDX-FileCopyrightText: 2025 OPDL Project Team
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
 */
package org.kde.kdeconnect.Helpers;

import android.content.Context;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Performance metrics engine for OPDL system.
 * Tracks real-time transfer performance, compares against benchmarks,
 * and provides performance status indicators.
 */
public class PerformanceMetricsEngine {
    private static final String TAG = "PerfMetricsEngine";
    private static PerformanceMetricsEngine instance;
    
    // Performance thresholds
    private static final double OPTIMAL_THRESHOLD = 0.95;  // 95% of expected
    private static final double WARNING_THRESHOLD = 0.70;  // 70% of expected
    
    // Current metrics state
    private volatile boolean isTransferActive = false;
    private volatile TransferMetrics currentMetrics;
    private volatile WiFiGenerationDetector.WiFiGeneration currentWiFiGeneration = 
            WiFiGenerationDetector.WiFiGeneration.UNKNOWN;
    
    // Listeners
    private final List<PerformanceMetricsListener> listeners = new CopyOnWriteArrayList<>();
    
    public interface PerformanceMetricsListener {
        void onMetricsUpdated(TransferMetrics metrics);
        void onPerformanceStatusChanged(PerformanceStatus status);
        void onTransferStarted(String fileName, long fileSize);
        void onTransferCompleted();
    }
    
    public enum PerformanceStatus {
        OPTIMAL("Optimal", "✓"),
        WARNING("Below Expected", "⚠"),
        ERROR("Transport Bottleneck", "⛔");
        
        private final String displayName;
        private final String emoji;
        
        PerformanceStatus(String displayName, String emoji) {
            this.displayName = displayName;
            this.emoji = emoji;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getEmoji() {
            return emoji;
        }
    }
    
    private PerformanceMetricsEngine() {}
    
    public static synchronized PerformanceMetricsEngine getInstance() {
        if (instance == null) {
            instance = new PerformanceMetricsEngine();
        }
        return instance;
    }
    
    /**
     * Initialize the performance engine with current WiFi context
     */
    public void initialize(Context context) {
        currentWiFiGeneration = WiFiGenerationDetector.detectCurrentWiFiGeneration(context);
        Log.d(TAG, "Initialized with WiFi generation: " + currentWiFiGeneration.getDisplayName());
    }
    
    /**
     * Start tracking a new transfer
     */
    public void startTransfer(String fileName, long fileSize, boolean isUpload) {
        isTransferActive = true;
        currentMetrics = new TransferMetrics(fileName, fileSize, isUpload, currentWiFiGeneration);
        
        // Notify listeners
        for (PerformanceMetricsListener listener : listeners) {
            listener.onTransferStarted(fileName, fileSize);
            listener.onMetricsUpdated(currentMetrics);
        }
        
        Log.d(TAG, "Started transfer: " + fileName + " (" + fileSize + " bytes)");
    }
    
    /**
     * Update transfer progress and metrics
     */
    public void updateTransferProgress(long bytesTransferred, long totalBytes, long elapsedTimeMs) {
        if (!isTransferActive || currentMetrics == null) return;
        
        currentMetrics.updateProgress(bytesTransferred, totalBytes, elapsedTimeMs);
        PerformanceStatus newStatus = calculatePerformanceStatus(currentMetrics);
        currentMetrics.setPerformanceStatus(newStatus);
        
        // Notify listeners
        for (PerformanceMetricsListener listener : listeners) {
            listener.onMetricsUpdated(currentMetrics);
            listener.onPerformanceStatusChanged(newStatus);
        }
    }
    
    /**
     * End current transfer
     */
    public void endTransfer() {
        if (!isTransferActive) return;
        
        isTransferActive = false;
        if (currentMetrics != null) {
            currentMetrics.markCompleted();
        }
        
        // Notify listeners
        for (PerformanceMetricsListener listener : listeners) {
            listener.onTransferCompleted();
            if (currentMetrics != null) {
                listener.onMetricsUpdated(currentMetrics);
            }
        }
        
        Log.d(TAG, "Transfer completed");
    }
    
    /**
     * Calculate performance status based on current metrics vs benchmarks
     */
    private PerformanceStatus calculatePerformanceStatus(TransferMetrics metrics) {
        WiFiGenerationDetector.WiFiBenchmarkProfile benchmark = 
                WiFiGenerationDetector.getBenchmarkProfile(metrics.getWiFiGeneration());
        
        double expectedThroughput = benchmark.getExpectedThroughput(); // MB/s
        double actualThroughput = metrics.getCurrentThroughput(); // MB/s
        
        double performanceRatio = actualThroughput / expectedThroughput;
        
        if (performanceRatio >= OPTIMAL_THRESHOLD) {
            return PerformanceStatus.OPTIMAL;
        } else if (performanceRatio >= WARNING_THRESHOLD) {
            return PerformanceStatus.WARNING;
        } else {
            return PerformanceStatus.ERROR;
        }
    }
    
    /**
     * Get current WiFi generation
     */
    public WiFiGenerationDetector.WiFiGeneration getCurrentWiFiGeneration() {
        return currentWiFiGeneration;
    }
    
    /**
     * Refresh WiFi generation detection
     */
    public void refreshWiFiGeneration(Context context) {
        WiFiGenerationDetector.WiFiGeneration newGeneration = 
                WiFiGenerationDetector.detectCurrentWiFiGeneration(context);
        
        if (newGeneration != currentWiFiGeneration) {
            currentWiFiGeneration = newGeneration;
            Log.d(TAG, "WiFi generation changed to: " + newGeneration.getDisplayName());
            
            // Update current metrics if transfer is active
            if (isTransferActive && currentMetrics != null) {
                currentMetrics.updateWiFiGeneration(newGeneration);
            }
        }
    }
    
    /**
     * Add performance metrics listener
     */
    public void addListener(PerformanceMetricsListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove performance metrics listener
     */
    public void removeListener(PerformanceMetricsListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Get current transfer metrics
     */
    public TransferMetrics getCurrentMetrics() {
        return currentMetrics;
    }
    
    /**
     * Check if transfer is currently active
     */
    public boolean isTransferActive() {
        return isTransferActive;
    }
}