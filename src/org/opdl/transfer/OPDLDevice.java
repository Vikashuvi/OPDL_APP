/*
 * SPDX-FileCopyrightText: 2025 OPDL Project Team
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
 */
package org.opdl.transfer;

import org.opdl.transfer.Helpers.WiFiGenerationDetector;
import org.opdl.transfer.R;

/**
 * OPDL Device model representing a performance-aware peer device.
 * Contains WiFi generation detection, transport verification, and performance metrics.
 */
public class OPDLDevice {
    private final String deviceId;
    private final String deviceName;
    private final DeviceType deviceType;
    
    // WiFi and transport information
    private WiFiGenerationDetector.WiFiGeneration wifiGeneration;
    private String chipset; // e.g., "Qualcomm Atheros QCA9377"
    private boolean chipsetVerified;
    private int rssi; // Signal strength in dBm
    private int meshHops; // Number of mesh hops (0 for direct connection)
    
    // Performance metrics
    private double currentThroughput; // MB/s
    private double currentLatency;    // ms
    private double efficiency;        // %
    private TransportStatus transportStatus;
    
    // Connection state
    private boolean isReachable;
    private boolean isPaired;
    private long lastSeenTimestamp;
    
    public enum DeviceType {
        PHONE("📱 Phone"),
        TABLET("📱 Tablet"), 
        DESKTOP("💻 Desktop"),
        LAPTOP("💻 Laptop"),
        TV("📺 TV"),
        UNKNOWN("❓ Unknown");
        
        private final String displayName;
        
        DeviceType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum TransportStatus {
        CONNECTED("Connected", "✅"),
        VERIFYING("Verifying", "🔍"),
        OPTIMAL("Optimal", "✓"),
        WARNING("Degraded", "⚠"),
        ERROR("Bottleneck", "⛔"),
        DISCONNECTED("Disconnected", "❌");
        
        private final String displayName;
        private final String emoji;
        
        TransportStatus(String displayName, String emoji) {
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
    
    public OPDLDevice(String deviceId, String deviceName, DeviceType deviceType) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.wifiGeneration = WiFiGenerationDetector.WiFiGeneration.UNKNOWN;
        this.chipset = "Unknown";
        this.chipsetVerified = false;
        this.rssi = -100; // Poor signal initially
        this.meshHops = 0;
        this.currentThroughput = 0.0;
        this.currentLatency = 0.0;
        this.efficiency = 0.0;
        this.transportStatus = TransportStatus.DISCONNECTED;
        this.isReachable = false;
        this.isPaired = false;
        this.lastSeenTimestamp = System.currentTimeMillis();
    }
    
    // Getters and Setters
    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public DeviceType getDeviceType() { return deviceType; }
    
    public WiFiGenerationDetector.WiFiGeneration getWifiGeneration() { return wifiGeneration; }
    public void setWifiGeneration(WiFiGenerationDetector.WiFiGeneration generation) { 
        this.wifiGeneration = generation; 
    }
    
    public String getChipset() { return chipset; }
    public void setChipset(String chipset) { this.chipset = chipset; }
    
    public boolean isChipsetVerified() { return chipsetVerified; }
    public void setChipsetVerified(boolean verified) { this.chipsetVerified = verified; }
    
    public int getRssi() { return rssi; }
    public void setRssi(int rssi) { this.rssi = rssi; }
    
    public int getMeshHops() { return meshHops; }
    public void setMeshHops(int hops) { this.meshHops = hops; }
    
    public double getCurrentThroughput() { return currentThroughput; }
    public void setCurrentThroughput(double throughput) { this.currentThroughput = throughput; }
    
    public double getCurrentLatency() { return currentLatency; }
    public void setCurrentLatency(double latency) { this.currentLatency = latency; }
    
    public double getEfficiency() { return efficiency; }
    public void setEfficiency(double efficiency) { this.efficiency = efficiency; }
    
    public TransportStatus getTransportStatus() { return transportStatus; }
    public void setTransportStatus(TransportStatus status) { this.transportStatus = status; }
    
    public boolean isReachable() { return isReachable; }
    public void setReachable(boolean reachable) { 
        this.isReachable = reachable;
        if (!reachable) {
            this.transportStatus = TransportStatus.DISCONNECTED;
        } else if (this.transportStatus == TransportStatus.DISCONNECTED) {
            this.transportStatus = TransportStatus.CONNECTED;
        }
    }
    
    public boolean isPaired() { return isPaired; }
    public void setPaired(boolean paired) { this.isPaired = paired; }
    
    public long getLastSeenTimestamp() { return lastSeenTimestamp; }
    public void updateLastSeen() { this.lastSeenTimestamp = System.currentTimeMillis(); }
    
    // Convenience methods for UI display
    public String getWifiGenerationString() {
        if (wifiGeneration == WiFiGenerationDetector.WiFiGeneration.UNKNOWN) {
            return "WiFi Generation: Detecting...";
        }
        String verified = chipsetVerified ? " (Verified)" : "";
        return wifiGeneration.getDisplayName() + verified;
    }
    
    public String getSignalStrengthString() {
        WiFiGenerationDetector.SignalQuality quality = 
                WiFiGenerationDetector.getSignalQuality(rssi);
        return rssi + " dBm (" + quality.getDisplayName() + ")";
    }
    
    public String getThroughputString() {
        return String.format("%.1f MB/s", currentThroughput);
    }
    
    public String getLatencyString() {
        return String.format("%.1f ms", currentLatency);
    }
    
    public String getEfficiencyString() {
        return String.format("%.1f%%", efficiency);
    }
    
    public String getStatusString() {
        return transportStatus.getEmoji() + " " + transportStatus.getDisplayName();
    }
    
    public String getDeviceTypeString() {
        return deviceType.getDisplayName();
    }
    
    /**
     * Get the appropriate color resource for the current transport status
     */
    public int getStatusColorResource() {
        switch (transportStatus) {
            case OPTIMAL:
                return R.color.opdl_performance_optimal;
            case WARNING:
                return R.color.opdl_performance_warning;
            case ERROR:
                return R.color.opdl_performance_error;
            case CONNECTED:
            case VERIFYING:
                return R.color.opdl_primary;
            default:
                return R.color.opdl_text_secondary;
        }
    }
    
    /**
     * Get the appropriate WiFi generation color
     */
    public int getWifiGenerationColorResource() {
        switch (wifiGeneration) {
            case WIFI_4:
                return R.color.opdl_wifi_4;
            case WIFI_5:
                return R.color.opdl_wifi_5;
            case WIFI_6E:
                return R.color.opdl_wifi_6e;
            default:
                return R.color.opdl_text_secondary;
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        OPDLDevice that = (OPDLDevice) obj;
        return deviceId.equals(that.deviceId);
    }
    
    @Override
    public int hashCode() {
        return deviceId.hashCode();
    }
    
    @Override
    public String toString() {
        return "OPDLDevice{" +
                "name='" + deviceName + '\'' +
                ", type=" + deviceType +
                ", wifi=" + wifiGeneration.getDisplayName() +
                ", status=" + transportStatus.getDisplayName() +
                '}';
    }
}