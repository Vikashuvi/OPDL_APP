/*
 * SPDX-FileCopyrightText: 2025 OPDL Project Team
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
 */
package org.opdl.transfer;

import java.util.ArrayList;
import java.util.List;

/**
 * Performance dashboard data model for OPDL system.
 * Aggregates device information, network statistics, and performance metrics.
 */
public class OPDLPerformanceDashboard {
    private final List<OPDLDevice> connectedDevices;
    private final NetworkStatistics networkStats;
    private final SystemMetrics systemMetrics;
    
    private volatile boolean isMonitoringActive;
    private long lastUpdateTimestamp;
    
    public OPDLPerformanceDashboard() {
        this.connectedDevices = new ArrayList<>();
        this.networkStats = new NetworkStatistics();
        this.systemMetrics = new SystemMetrics();
        this.isMonitoringActive = false;
        this.lastUpdateTimestamp = System.currentTimeMillis();
    }
    
    /**
     * Add a device to the dashboard
     */
    public void addDevice(OPDLDevice device) {
        if (!connectedDevices.contains(device)) {
            connectedDevices.add(device);
            device.updateLastSeen();
        }
    }
    
    /**
     * Remove a device from the dashboard
     */
    public void removeDevice(OPDLDevice device) {
        connectedDevices.remove(device);
    }
    
    /**
     * Update device information
     */
    public void updateDevice(OPDLDevice device) {
        int index = connectedDevices.indexOf(device);
        if (index >= 0) {
            connectedDevices.set(index, device);
            device.updateLastSeen();
        }
    }
    
    /**
     * Get device by ID
     */
    public OPDLDevice getDevice(String deviceId) {
        for (OPDLDevice device : connectedDevices) {
            if (device.getDeviceId().equals(deviceId)) {
                return device;
            }
        }
        return null;
    }
    
    /**
     * Start performance monitoring
     */
    public void startMonitoring() {
        this.isMonitoringActive = true;
        this.lastUpdateTimestamp = System.currentTimeMillis();
    }
    
    /**
     * Stop performance monitoring
     */
    public void stopMonitoring() {
        this.isMonitoringActive = false;
    }
    
    /**
     * Update dashboard with latest metrics
     */
    public void updateDashboard() {
        this.lastUpdateTimestamp = System.currentTimeMillis();
        this.networkStats.updateStatistics(connectedDevices);
        this.systemMetrics.updateMetrics();
    }
    
    // Getters
    public List<OPDLDevice> getConnectedDevices() { return new ArrayList<>(connectedDevices); }
    public NetworkStatistics getNetworkStats() { return networkStats; }
    public SystemMetrics getSystemMetrics() { return systemMetrics; }
    public boolean isMonitoringActive() { return isMonitoringActive; }
    public long getLastUpdateTimestamp() { return lastUpdateTimestamp; }
    
    /**
     * Get count of devices by transport status
     */
    public int getDeviceCountByStatus(OPDLDevice.TransportStatus status) {
        int count = 0;
        for (OPDLDevice device : connectedDevices) {
            if (device.getTransportStatus() == status) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Get devices grouped by WiFi generation
     */
    public java.util.Map<org.opdl.transfer.Helpers.WiFiGenerationDetector.WiFiGeneration, List<OPDLDevice>> 
            getDevicesByWiFiGeneration() {
        java.util.Map<org.opdl.transfer.Helpers.WiFiGenerationDetector.WiFiGeneration, List<OPDLDevice>> 
                grouped = new java.util.HashMap<>();
        
        for (OPDLDevice device : connectedDevices) {
            org.opdl.transfer.Helpers.WiFiGenerationDetector.WiFiGeneration gen = device.getWifiGeneration();
            if (!grouped.containsKey(gen)) {
                grouped.put(gen, new ArrayList<>());
            }
            grouped.get(gen).add(device);
        }
        
        return grouped;
    }
    
    /**
     * Network statistics data class
     */
    public static class NetworkStatistics {
        private int totalDevices;
        private int optimalDevices;
        private int warningDevices;
        private int errorDevices;
        private double averageThroughput;
        private double averageLatency;
        private double networkEfficiency;
        
        private org.opdl.transfer.Helpers.WiFiGenerationDetector.WiFiGeneration dominantWiFiGeneration;
        
        public void updateStatistics(List<OPDLDevice> devices) {
            this.totalDevices = devices.size();
            this.optimalDevices = 0;
            this.warningDevices = 0;
            this.errorDevices = 0;
            double throughputSum = 0;
            double latencySum = 0;
            int validDeviceCount = 0;
            
            // Count devices by status and sum metrics
            for (OPDLDevice device : devices) {
                switch (device.getTransportStatus()) {
                    case OPTIMAL:
                        optimalDevices++;
                        break;
                    case WARNING:
                        warningDevices++;
                        break;
                    case ERROR:
                        errorDevices++;
                        break;
                }
                
                if (device.getCurrentThroughput() > 0) {
                    throughputSum += device.getCurrentThroughput();
                    latencySum += device.getCurrentLatency();
                    validDeviceCount++;
                }
            }
            
            // Calculate averages
            this.averageThroughput = validDeviceCount > 0 ? throughputSum / validDeviceCount : 0;
            this.averageLatency = validDeviceCount > 0 ? latencySum / validDeviceCount : 0;
            
            // Calculate network efficiency
            if (validDeviceCount > 0) {
                double efficiencySum = 0;
                for (OPDLDevice device : devices) {
                    efficiencySum += device.getEfficiency();
                }
                this.networkEfficiency = efficiencySum / validDeviceCount;
            } else {
                this.networkEfficiency = 0;
            }
            
            // Determine dominant WiFi generation
            java.util.Map<org.opdl.transfer.Helpers.WiFiGenerationDetector.WiFiGeneration, Integer> genCount = 
                    new java.util.HashMap<>();
            for (OPDLDevice device : devices) {
                org.opdl.transfer.Helpers.WiFiGenerationDetector.WiFiGeneration gen = device.getWifiGeneration();
                genCount.put(gen, genCount.getOrDefault(gen, 0) + 1);
            }
            
            this.dominantWiFiGeneration = org.opdl.transfer.Helpers.WiFiGenerationDetector.WiFiGeneration.UNKNOWN;
            int maxCount = 0;
            for (java.util.Map.Entry<org.opdl.transfer.Helpers.WiFiGenerationDetector.WiFiGeneration, Integer> entry : genCount.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    this.dominantWiFiGeneration = entry.getKey();
                }
            }
        }
        
        // Getters
        public int getTotalDevices() { return totalDevices; }
        public int getOptimalDevices() { return optimalDevices; }
        public int getWarningDevices() { return warningDevices; }
        public int getErrorDevices() { return errorDevices; }
        public double getAverageThroughput() { return averageThroughput; }
        public double getAverageLatency() { return averageLatency; }
        public double getNetworkEfficiency() { return networkEfficiency; }
        public org.opdl.transfer.Helpers.WiFiGenerationDetector.WiFiGeneration getDominantWiFiGeneration() { 
            return dominantWiFiGeneration; 
        }
        
        public String getAverageThroughputString() {
            return String.format("%.1f MB/s", averageThroughput);
        }
        
        public String getAverageLatencyString() {
            return String.format("%.1f ms", averageLatency);
        }
        
        public String getNetworkEfficiencyString() {
            return String.format("%.1f%%", networkEfficiency);
        }
        
        public String getDominantWiFiGenerationString() {
            return dominantWiFiGeneration.getDisplayName();
        }
    }
    
    /**
     * System metrics data class
     */
    public static class SystemMetrics {
        private long availableMemory; // MB
        private long totalMemory;     // MB
        private double cpuUsage;      // %
        private long networkRxBytes;  // bytes
        private long networkTxBytes;  // bytes
        private long uptimeSeconds;
        
        public void updateMetrics() {
            // In a real implementation, this would query system metrics
            // For now, using simulated values
            
            this.availableMemory = 2048 + (long)(Math.random() * 2048); // 2-4 GB
            this.totalMemory = 4096; // 4 GB
            this.cpuUsage = 10 + (Math.random() * 40); // 10-50%
            this.networkRxBytes = (long)(Math.random() * 1000000000); // Up to 1 GB
            this.networkTxBytes = (long)(Math.random() * 1000000000); // Up to 1 GB
            this.uptimeSeconds = System.currentTimeMillis() / 1000;
        }
        
        // Getters
        public long getAvailableMemory() { return availableMemory; }
        public long getTotalMemory() { return totalMemory; }
        public double getCpuUsage() { return cpuUsage; }
        public long getNetworkRxBytes() { return networkRxBytes; }
        public long getNetworkTxBytes() { return networkTxBytes; }
        public long getUptimeSeconds() { return uptimeSeconds; }
        
        public String getMemoryUsageString() {
            return String.format("%d/%d MB", totalMemory - availableMemory, totalMemory);
        }
        
        public String getCpuUsageString() {
            return String.format("%.1f%%", cpuUsage);
        }
        
        public String getNetworkTrafficString() {
            double rxMB = networkRxBytes / (1024.0 * 1024.0);
            double txMB = networkTxBytes / (1024.0 * 1024.0);
            return String.format("RX: %.1f MB | TX: %.1f MB", rxMB, txMB);
        }
        
        public String getUptimeString() {
            long hours = uptimeSeconds / 3600;
            long minutes = (uptimeSeconds % 3600) / 60;
            return String.format("%d:%02d", hours, minutes);
        }
    }
}