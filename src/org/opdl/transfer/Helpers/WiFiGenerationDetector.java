/*
 * SPDX-FileCopyrightText: 2025 OPDL Project Team
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
 */
package org.opdl.transfer.Helpers;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

/**
 * Utility class for detecting WiFi generation and providing performance benchmarks.
 * Supports WiFi 4 (802.11n), WiFi 5 (802.11ac), and WiFi 6E (802.11ax) detection.
 */
public class WiFiGenerationDetector {
    private static final String TAG = "WiFiGenDetector";
    
    public enum WiFiGeneration {
        UNKNOWN("Unknown", 0),
        WIFI_4("WiFi 4", 150),      // 802.11n - ~150 Mbps typical
        WIFI_5("WiFi 5", 433),      // 802.11ac - ~433 Mbps typical  
        WIFI_6E("WiFi 6E", 1200);   // 802.11ax - ~1200 Mbps typical
        
        private final String displayName;
        private final int typicalLinkSpeed;
        
        WiFiGeneration(String displayName, int typicalLinkSpeed) {
            this.displayName = displayName;
            this.typicalLinkSpeed = typicalLinkSpeed;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public int getTypicalLinkSpeed() {
            return typicalLinkSpeed;
        }
    }
    
    /**
     * Detect the current WiFi generation based on connection information
     */
    public static WiFiGeneration detectCurrentWiFiGeneration(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            
            if (wifiManager == null) {
                Log.w(TAG, "WiFi manager is null");
                return WiFiGeneration.UNKNOWN;
            }
            
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo == null) {
                Log.w(TAG, "WiFi info is null");
                return WiFiGeneration.UNKNOWN;
            }
            
            // API 30+ has direct WiFi standard detection
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                int wifiStandard = wifiInfo.getWifiStandard();
                switch (wifiStandard) {
                    case ScanResult.WIFI_STANDARD_11AX:
                        Log.d(TAG, "Detected WiFi 6/6E (802.11ax)");
                        return WiFiGeneration.WIFI_6E;
                    case ScanResult.WIFI_STANDARD_11AC:
                        Log.d(TAG, "Detected WiFi 5 (802.11ac)");
                        return WiFiGeneration.WIFI_5;
                    case ScanResult.WIFI_STANDARD_11N:
                        Log.d(TAG, "Detected WiFi 4 (802.11n)");
                        return WiFiGeneration.WIFI_4;
                    default:
                        Log.d(TAG, "Unknown WiFi standard: " + wifiStandard);
                        break;
                }
            }
            
            // Fallback method using link speed heuristic
            int linkSpeed = wifiInfo.getLinkSpeed(); // Mbps
            Log.d(TAG, "Link speed: " + linkSpeed + " Mbps");
            
            if (linkSpeed >= 867) {
                // WiFi 5 typically starts around 433 Mbps per spatial stream
                // High-end WiFi 5 can reach ~867 Mbps (2x2 MIMO)
                return WiFiGeneration.WIFI_5;
            } else if (linkSpeed >= 300) {
                // WiFi 4 (802.11n) with 2x2 MIMO can reach ~300 Mbps
                return WiFiGeneration.WIFI_4;
            } else if (linkSpeed >= 150) {
                // WiFi 4 (802.11n) with 1x1 MIMO typically ~150 Mbps
                return WiFiGeneration.WIFI_4;
            } else if (linkSpeed > 0) {
                // Very low speeds, likely WiFi 4 or poor connection
                return WiFiGeneration.WIFI_4;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error detecting WiFi generation", e);
        }
        
        return WiFiGeneration.UNKNOWN;
    }
    
    /**
     * Get expected throughput benchmark for a given WiFi generation
     */
    public static WiFiBenchmarkProfile getBenchmarkProfile(WiFiGeneration generation) {
        switch (generation) {
            case WIFI_4:
                return new WiFiBenchmarkProfile(
                    generation,
                    148.5,  // Expected throughput MB/s
                    18.6,   // Expected latency ms
                    85.0    // Expected efficiency %
                );
            case WIFI_5:
                return new WiFiBenchmarkProfile(
                    generation,
                    245.0,  // Expected throughput MB/s
                    30.6,   // Expected latency ms
                    92.0    // Expected efficiency %
                );
            case WIFI_6E:
                return new WiFiBenchmarkProfile(
                    generation,
                    1334.8, // Expected throughput MB/s
                    166.8,  // Expected latency ms
                    96.0    // Expected efficiency %
                );
            default:
                return new WiFiBenchmarkProfile(
                    WiFiGeneration.UNKNOWN,
                    50.0,   // Conservative fallback
                    50.0,   // Conservative fallback
                    70.0    // Conservative fallback
                );
        }
    }
    
    /**
     * Get RSSI signal strength quality indicator
     */
    public static SignalQuality getSignalQuality(int rssi) {
        if (rssi >= -50) {
            return SignalQuality.EXCELLENT;
        } else if (rssi >= -60) {
            return SignalQuality.GOOD;
        } else if (rssi >= -70) {
            return SignalQuality.FAIR;
        } else if (rssi >= -80) {
            return SignalQuality.POOR;
        } else {
            return SignalQuality.VERY_POOR;
        }
    }
    
    /**
     * WiFi benchmark profile containing expected performance metrics
     */
    public static class WiFiBenchmarkProfile {
        private final WiFiGeneration generation;
        private final double expectedThroughput; // MB/s
        private final double expectedLatency;    // ms
        private final double expectedEfficiency; // %
        
        public WiFiBenchmarkProfile(WiFiGeneration generation, double throughput, 
                                  double latency, double efficiency) {
            this.generation = generation;
            this.expectedThroughput = throughput;
            this.expectedLatency = latency;
            this.expectedEfficiency = efficiency;
        }
        
        public WiFiGeneration getGeneration() { return generation; }
        public double getExpectedThroughput() { return expectedThroughput; }
        public double getExpectedLatency() { return expectedLatency; }
        public double getExpectedEfficiency() { return expectedEfficiency; }
        
        public String getThroughputString() {
            return String.format("%.1f MB/s", expectedThroughput);
        }
        
        public String getLatencyString() {
            return String.format("%.1f ms", expectedLatency);
        }
    }
    
    /**
     * Signal quality enumeration based on RSSI values
     */
    public enum SignalQuality {
        EXCELLENT("Excellent", -50),
        GOOD("Good", -60),
        FAIR("Fair", -70),
        POOR("Poor", -80),
        VERY_POOR("Very Poor", -90);
        
        private final String displayName;
        private final int threshold;
        
        SignalQuality(String displayName, int threshold) {
            this.displayName = displayName;
            this.threshold = threshold;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public int getThreshold() {
            return threshold;
        }
    }
}