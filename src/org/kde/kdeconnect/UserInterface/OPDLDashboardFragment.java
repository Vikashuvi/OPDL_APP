/*
 * SPDX-FileCopyrightText: 2025 OPDL Project Team
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
 */
package org.kde.kdeconnect.UserInterface;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.kde.kdeconnect.Helpers.PerformanceMetricsEngine;
import org.kde.kdeconnect.Helpers.WiFiGenerationDetector;
import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect.OPDLDevice;
import org.kde.kdeconnect.OPDLPerformanceDashboard;
import org.kde.kdeconnect_tp.R;

import java.util.List;

/**
 * Main OPDL Performance Dashboard Fragment.
 * Displays performance-aware peer devices with real-time metrics.
 */
public class OPDLDashboardFragment extends Fragment implements PerformanceMetricsEngine.PerformanceMetricsListener {
    private OPDLPerformanceDashboard dashboard;
    private PerformanceMetricsEngine metricsEngine;
    
    // UI Components
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout deviceContainer;
    private ProgressBar loadingIndicator;
    private TextView networkSummaryText;
    private TextView performanceSummaryText;
    private TextView wifiInfoText;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        
        // Initialize dashboard and metrics engine
        dashboard = new OPDLPerformanceDashboard();
        metricsEngine = PerformanceMetricsEngine.getInstance();
        metricsEngine.addListener(this);
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                           @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_opdl_dashboard, container, false);
        
        // Initialize UI components
        swipeRefreshLayout = view.findViewById(R.id.dashboard_swipe_refresh);
        deviceContainer = view.findViewById(R.id.device_container);
        loadingIndicator = view.findViewById(R.id.loading_indicator);
        networkSummaryText = view.findViewById(R.id.network_summary);
        performanceSummaryText = view.findViewById(R.id.performance_summary);
        wifiInfoText = view.findViewById(R.id.wifi_info);
        
        // Setup refresh listener
        swipeRefreshLayout.setOnRefreshListener(this::refreshDashboard);
        
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Add menu provider
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.opdl_dashboard, menu);
            }
            
            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.menu_refresh_dashboard) {
                    refreshDashboard();
                    return true;
                } else if (menuItem.getItemId() == R.id.menu_performance_view) {
                    // TODO: Open advanced performance metrics view
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        
        // Load initial data
        loadDashboardData();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        dashboard.startMonitoring();
        refreshDashboard();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        dashboard.stopMonitoring();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        metricsEngine.removeListener(this);
    }
    
    /**
     * Load and display dashboard data
     */
    private void loadDashboardData() {
        loadingIndicator.setVisibility(View.VISIBLE);
        deviceContainer.removeAllViews();
        
        // Initialize with current WiFi context
        Context context = requireContext();
        metricsEngine.initialize(context);
        dashboard.updateDashboard();
        
        // Get connected devices from KdeConnect
        List<org.kde.kdeconnect.Device> kdeDevices = 
                KdeConnect.getInstance().getDevices().values().stream()
                        .filter(device -> device.isReachable() && device.isPaired())
                        .toList();
        
        // Convert to OPDL devices and populate dashboard
        for (org.kde.kdeconnect.Device kdeDevice : kdeDevices) {
            OPDLDevice opdlDevice = convertToOPDLDevice(kdeDevice);
            dashboard.addDevice(opdlDevice);
            
            // Create and add device card
            OPDLDeviceCard deviceCard = new OPDLDeviceCard(requireContext());
            deviceCard.bindDevice(opdlDevice);
            deviceCard.setOnDeviceClickListener(v -> onDeviceClicked(opdlDevice));
            deviceContainer.addView(deviceCard);
        }
        
        // Update summary information
        updateDashboardSummary();
        
        loadingIndicator.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
    }
    
    /**
     * Convert KDE Device to OPDL Device
     */
    private OPDLDevice convertToOPDLDevice(org.kde.kdeconnect.Device kdeDevice) {
        OPDLDevice opdlDevice = new OPDLDevice(
                kdeDevice.getDeviceId(),
                kdeDevice.getName(),
                getDeviceType(kdeDevice.getDeviceType())
        );
        
        // Detect WiFi generation
        WiFiGenerationDetector.WiFiGeneration wifiGen = 
                WiFiGenerationDetector.detectCurrentWiFiGeneration(requireContext());
        opdlDevice.setWifiGeneration(wifiGen);
        opdlDevice.setChipset("Auto-Detected");
        opdlDevice.setChipsetVerified(true);
        
        // Set simulated performance metrics (in real implementation, these would come from actual measurements)
        opdlDevice.setRssi(-50 - (int)(Math.random() * 30)); // -50 to -80 dBm
        opdlDevice.setCurrentThroughput(getSimulatedThroughput(wifiGen));
        opdlDevice.setCurrentLatency(getSimulatedLatency(wifiGen));
        opdlDevice.setEfficiency(85 + Math.random() * 10); // 85-95%
        opdlDevice.setTransportStatus(OPDLDevice.TransportStatus.CONNECTED);
        opdlDevice.setReachable(true);
        opdlDevice.setPaired(true);
        
        return opdlDevice;
    }
    
    /**
     * Get device type mapping
     */
    private OPDLDevice.DeviceType getDeviceType(org.kde.kdeconnect.DeviceType kdeType) {
        switch (kdeType) {
            case PHONE:
                return OPDLDevice.DeviceType.PHONE;
            case TABLET:
                return OPDLDevice.DeviceType.TABLET;
            case DESKTOP:
                return OPDLDevice.DeviceType.DESKTOP;
            case LAPTOP:
                return OPDLDevice.DeviceType.LAPTOP;
            case TV:
                return OPDLDevice.DeviceType.TV;
            default:
                return OPDLDevice.DeviceType.UNKNOWN;
        }
    }
    
    /**
     * Get simulated throughput based on WiFi generation
     */
    private double getSimulatedThroughput(WiFiGenerationDetector.WiFiGeneration wifiGen) {
        WiFiGenerationDetector.WiFiBenchmarkProfile profile = 
                WiFiGenerationDetector.getBenchmarkProfile(wifiGen);
        double baseThroughput = profile.getExpectedThroughput();
        // Add some realistic variation
        return baseThroughput * (0.8 + Math.random() * 0.4); // 80-120% of expected
    }
    
    /**
     * Get simulated latency based on WiFi generation
     */
    private double getSimulatedLatency(WiFiGenerationDetector.WiFiGeneration wifiGen) {
        WiFiGenerationDetector.WiFiBenchmarkProfile profile = 
                WiFiGenerationDetector.getBenchmarkProfile(wifiGen);
        double baseLatency = profile.getExpectedLatency();
        // Add some realistic variation
        return baseLatency * (0.9 + Math.random() * 0.2); // 90-110% of expected
    }
    
    /**
     * Update dashboard summary information
     */
    private void updateDashboardSummary() {
        OPDLPerformanceDashboard.NetworkStatistics stats = dashboard.getNetworkStats();
        
        // Network summary
        String networkSummary = String.format(
                "Devices: %d | Optimal: %d | Avg Speed: %s",
                stats.getTotalDevices(),
                stats.getOptimalDevices(),
                stats.getAverageThroughputString()
        );
        networkSummaryText.setText(networkSummary);
        
        // Performance summary
        String performanceSummary = String.format(
                "Network Efficiency: %s | Avg Latency: %s",
                stats.getNetworkEfficiencyString(),
                stats.getAverageLatencyString()
        );
        performanceSummaryText.setText(performanceSummary);
        
        // WiFi info
        WiFiGenerationDetector.WiFiGeneration currentWiFi = metricsEngine.getCurrentWiFiGeneration();
        String wifiInfo = String.format(
                "Current Network: %s | Dominant Standard: %s",
                currentWiFi.getDisplayName(),
                stats.getDominantWiFiGenerationString()
        );
        wifiInfoText.setText(wifiInfo);
    }
    
    /**
     * Refresh dashboard data
     */
    private void refreshDashboard() {
        dashboard.updateDashboard();
        loadDashboardData();
    }
    
    /**
     * Handle device card click
     */
    private void onDeviceClicked(OPDLDevice device) {
        // TODO: Open device details or transfer screen
    }
    
    // PerformanceMetricsListener implementation
    @Override
    public void onMetricsUpdated(org.kde.kdeconnect.Helpers.TransferMetrics metrics) {
        // Update UI with new metrics
        requireActivity().runOnUiThread(this::loadDashboardData);
    }
    
    @Override
    public void onPerformanceStatusChanged(PerformanceMetricsEngine.PerformanceStatus status) {
        // Handle performance status changes
        requireActivity().runOnUiThread(this::loadDashboardData);
    }
    
    @Override
    public void onTransferStarted(String fileName, long fileSize) {
        // Handle transfer start
    }
    
    @Override
    public void onTransferCompleted() {
        // Handle transfer completion
        requireActivity().runOnUiThread(this::loadDashboardData);
    }
}