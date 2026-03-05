/*
 * SPDX-FileCopyrightText: 2025 OPDL Project Team
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
 */
package org.kde.kdeconnect.UserInterface;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import org.kde.kdeconnect.OPDLDevice;
import org.kde.kdeconnect_tp.R;

/**
 * Performance-Aware Peer Device Card for OPDL Dashboard.
 * Displays comprehensive device information with real-time performance metrics.
 */
public class OPDLDeviceCard extends LinearLayout {
    private OPDLDevice device;
    
    // UI Components
    private TextView deviceNameText;
    private TextView deviceTypeText;
    private TextView chipsetText;
    private TextView wifiGenerationText;
    private TextView rssiText;
    private TextView throughputText;
    private TextView latencyText;
    private TextView efficiencyText;
    private TextView meshHopsText;
    private TextView statusText;
    private CardView cardView;
    
    public OPDLDeviceCard(Context context) {
        super(context);
        init(context);
    }
    
    public OPDLDeviceCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public OPDLDeviceCard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.opdl_device_card, this, true);
        
        // Initialize UI components
        cardView = findViewById(R.id.device_card);
        deviceNameText = findViewById(R.id.device_name);
        deviceTypeText = findViewById(R.id.device_type);
        chipsetText = findViewById(R.id.chipset_info);
        wifiGenerationText = findViewById(R.id.wifi_generation);
        rssiText = findViewById(R.id.signal_strength);
        throughputText = findViewById(R.id.throughput);
        latencyText = findViewById(R.id.latency);
        efficiencyText = findViewById(R.id.efficiency);
        meshHopsText = findViewById(R.id.mesh_hops);
        statusText = findViewById(R.id.connection_status);
    }
    
    /**
     * Bind device data to UI components
     */
    public void bindDevice(OPDLDevice device) {
        this.device = device;
        
        if (device == null) return;
        
        // Update basic device info
        deviceNameText.setText(device.getDeviceName());
        deviceTypeText.setText(device.getDeviceTypeString());
        
        // Update chipset and WiFi info
        chipsetText.setText("Chipset: " + device.getChipset());
        wifiGenerationText.setText(device.getWifiGenerationString());
        wifiGenerationText.setTextColor(getResources().getColor(device.getWifiGenerationColorResource(), null));
        
        // Update signal strength
        rssiText.setText("RSSI: " + device.getSignalStrengthString());
        
        // Update performance metrics
        throughputText.setText("Throughput: " + device.getThroughputString());
        latencyText.setText("Latency: " + device.getLatencyString());
        efficiencyText.setText("Efficiency: " + device.getEfficiencyString());
        
        // Update mesh info
        meshHopsText.setText("Mesh Hops: " + device.getMeshHops());
        meshHopsText.setVisibility(device.getMeshHops() > 0 ? View.VISIBLE : View.GONE);
        
        // Update status with color coding
        statusText.setText(device.getStatusString());
        statusText.setTextColor(getResources().getColor(device.getStatusColorResource(), null));
        
        // Update card appearance based on status
        updateCardAppearance();
    }
    
    /**
     * Update card visual appearance based on device status
     */
    private void updateCardAppearance() {
        if (device == null) return;
        
        // Set card background based on performance status
        switch (device.getTransportStatus()) {
            case OPTIMAL:
                cardView.setCardBackgroundColor(getResources().getColor(R.color.opdl_card_background, null));
                break;
            case WARNING:
                cardView.setCardBackgroundColor(getResources().getColor(R.color.opdl_performance_warning, null));
                break;
            case ERROR:
                cardView.setCardBackgroundColor(getResources().getColor(R.color.opdl_performance_error, null));
                break;
            default:
                cardView.setCardBackgroundColor(getResources().getColor(R.color.opdl_surface_variant, null));
                break;
        }
    }
    
    /**
     * Get the bound device
     */
    public OPDLDevice getDevice() {
        return device;
    }
    
    /**
     * Set click listener for the card
     */
    public void setOnDeviceClickListener(OnClickListener listener) {
        cardView.setOnClickListener(listener);
    }
}