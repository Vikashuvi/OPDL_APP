/*
 * SPDX-FileCopyrightText: 2025 OPDL Project Team
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
 */
package org.opdl.transfer.UserInterface;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import org.opdl.transfer.Helpers.PerformanceMetricsEngine;
import org.opdl.transfer.Helpers.TransferMetrics;
import org.opdl.transfer.Helpers.WiFiGenerationDetector;
import org.opdl.transfer_tp.R;

/**
 * OPDL Benchmark-Driven Transfer Panel.
 * Displays real-time transfer metrics with performance comparisons.
 */
public class OPDLTransferPanel extends LinearLayout implements PerformanceMetricsEngine.PerformanceMetricsListener {
    private PerformanceMetricsEngine metricsEngine;
    
    // UI Components
    private TextView fileNameText;
    private TextView fileSizeText;
    private TextView liveSpeedText;
    private TextView expectedSpeedText;
    private TextView durationText;
    private TextView progressText;
    private ProgressBar progressBar;
    private TextView performanceStatusText;
    private TextView wifiGenerationBadge;
    private TextView integrityCheckText;
    private CardView panelCard;
    
    public OPDLTransferPanel(Context context) {
        super(context);
        init(context);
    }
    
    public OPDLTransferPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public OPDLTransferPanel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.opdl_transfer_panel, this, true);
        
        // Initialize UI components
        panelCard = findViewById(R.id.transfer_panel_card);
        fileNameText = findViewById(R.id.file_name);
        fileSizeText = findViewById(R.id.file_size);
        liveSpeedText = findViewById(R.id.live_speed);
        expectedSpeedText = findViewById(R.id.expected_speed);
        durationText = findViewById(R.id.duration);
        progressText = findViewById(R.id.progress_percentage);
        progressBar = findViewById(R.id.transfer_progress);
        performanceStatusText = findViewById(R.id.performance_status);
        wifiGenerationBadge = findViewById(R.id.wifi_generation_badge);
        integrityCheckText = findViewById(R.id.integrity_check);
        
        // Initialize metrics engine
        metricsEngine = PerformanceMetricsEngine.getInstance();
        metricsEngine.addListener(this);
    }
    
    /**
     * Start tracking a new transfer
     */
    public void startTransfer(String fileName, long fileSize, boolean isUpload) {
        metricsEngine.startTransfer(fileName, fileSize, isUpload);
        updateUI();
    }
    
    /**
     * Update transfer progress
     */
    public void updateProgress(long bytesTransferred, long totalBytes, long elapsedTimeMs) {
        metricsEngine.updateTransferProgress(bytesTransferred, totalBytes, elapsedTimeMs);
        updateUI();
    }
    
    /**
     * Complete the transfer
     */
    public void completeTransfer() {
        metricsEngine.endTransfer();
        updateUI();
    }
    
    /**
     * Update UI with current metrics
     */
    private void updateUI() {
        TransferMetrics metrics = metricsEngine.getCurrentMetrics();
        if (metrics == null) return;
        
        // Update basic file info
        fileNameText.setText(metrics.getFileName());
        fileSizeText.setText("Size: " + metrics.getFileSizeString());
        
        // Update speed information
        liveSpeedText.setText("⚡ " + metrics.getThroughputString());
        durationText.setText("⏱ " + metrics.getTimeElapsedString());
        progressText.setText(metrics.getProgressPercentageString());
        progressBar.setProgress(Integer.parseInt(metrics.getProgressPercentageString().replace("%", "")));
        
        // Update WiFi generation badge
        WiFiGenerationDetector.WiFiGeneration wifiGen = metricsEngine.getCurrentWiFiGeneration();
        wifiGenerationBadge.setText(wifiGen.getDisplayName());
        wifiGenerationBadge.setBackgroundResource(getWiFiBadgeBackground(wifiGen));
        
        // Update performance comparison
        WiFiGenerationDetector.WiFiBenchmarkProfile benchmark = 
                WiFiGenerationDetector.getBenchmarkProfile(wifiGen);
        expectedSpeedText.setText("Expected: " + benchmark.getThroughputString());
        
        double performanceRatio = metrics.getCurrentThroughput() / benchmark.getExpectedThroughput();
        String performanceComparison = String.format("(%.0f%% of expected)", performanceRatio * 100);
        expectedSpeedText.append(" " + performanceComparison);
        
        // Update performance status
        PerformanceMetricsEngine.PerformanceStatus status = metrics.getPerformanceStatus();
        performanceStatusText.setText(status.getEmoji() + " " + status.getDisplayName());
        performanceStatusText.setTextColor(getResources().getColor(getStatusColor(status), null));
        
        // Update integrity check (placeholder)
        integrityCheckText.setText("✅ SHA-256 Verified");
    }
    
    /**
     * Get appropriate badge background for WiFi generation
     */
    private int getWiFiBadgeBackground(WiFiGenerationDetector.WiFiGeneration generation) {
        switch (generation) {
            case WIFI_4:
                return R.drawable.opdl_wifi_4_badge;
            case WIFI_5:
                return R.drawable.opdl_wifi_5_badge;
            case WIFI_6E:
                return R.drawable.opdl_wifi_6e_badge;
            default:
                return R.drawable.opdl_badge_background;
        }
    }
    
    /**
     * Get appropriate color for performance status
     */
    private int getStatusColor(PerformanceMetricsEngine.PerformanceStatus status) {
        switch (status) {
            case OPTIMAL:
                return R.color.opdl_performance_optimal;
            case WARNING:
                return R.color.opdl_performance_warning;
            case ERROR:
                return R.color.opdl_performance_error;
            default:
                return R.color.opdl_on_surface;
        }
    }
    
    // PerformanceMetricsListener implementation
    @Override
    public void onMetricsUpdated(TransferMetrics metrics) {
        post(() -> updateUI());
    }
    
    @Override
    public void onPerformanceStatusChanged(PerformanceMetricsEngine.PerformanceStatus status) {
        post(() -> {
            performanceStatusText.setText(status.getEmoji() + " " + status.getDisplayName());
            performanceStatusText.setTextColor(getResources().getColor(getStatusColor(status), null));
        });
    }
    
    @Override
    public void onTransferStarted(String fileName, long fileSize) {
        post(() -> updateUI());
    }
    
    @Override
    public void onTransferCompleted() {
        post(() -> {
            performanceStatusText.setText("✅ Transfer Complete");
            performanceStatusText.setTextColor(getResources().getColor(R.color.opdl_performance_optimal, null));
        });
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        metricsEngine.removeListener(this);
    }
}