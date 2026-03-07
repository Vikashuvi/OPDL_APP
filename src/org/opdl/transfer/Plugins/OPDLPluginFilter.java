/*
 * SPDX-FileCopyrightText: 2025 OPDL Project Team
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
 */
package org.opdl.transfer.Plugins;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Plugin filter for OPDL system.
 * Defines which plugins are included/excluded in the performance-focused OPDL mode.
 */
public class OPDLPluginFilter {
    // Plugins to exclude from OPDL (non-transfer related functionality)
    private static final Set<String> EXCLUDED_PLUGINS = new HashSet<>(Arrays.asList(
        "org.opdl.transfer.Plugins.NotificationsPlugin.NotificationsPlugin",
        "org.opdl.transfer.Plugins.ReceiveNotificationsPlugin.ReceiveNotificationsPlugin",
        "org.opdl.transfer.Plugins.TelephonyPlugin.TelephonyPlugin",
        "org.opdl.transfer.Plugins.SMSPlugin.SMSPlugin",
        "org.opdl.transfer.Plugins.ContactsPlugin.ContactsPlugin",
        "org.opdl.transfer.Plugins.MprisPlugin.MprisPlugin",
        "org.opdl.transfer.Plugins.MprisReceiverPlugin.MprisReceiverPlugin",
        "org.opdl.transfer.Plugins.PresenterPlugin.PresenterPlugin",
        "org.opdl.transfer.Plugins.FindMyPhonePlugin.FindMyPhonePlugin",
        "org.opdl.transfer.Plugins.FindRemoteDevicePlugin.FindRemoteDevicePlugin",
        "org.opdl.transfer.Plugins.SystemVolumePlugin.SystemVolumePlugin",
        "org.opdl.transfer.Plugins.BatteryPlugin.BatteryPlugin",
        "org.opdl.transfer.Plugins.ConnectivityReportPlugin.ConnectivityReportPlugin"
    ));
    
    // Core plugins that should always be included
    private static final Set<String> CORE_PLUGINS = new HashSet<>(Arrays.asList(
        "org.opdl.transfer.Plugins.SharePlugin.SharePlugin",
        "org.opdl.transfer.Plugins.PingPlugin.PingPlugin",
        "org.opdl.transfer.Plugins.SftpPlugin.SftpPlugin",
        "org.opdl.transfer.Plugins.ClipboardPlugin.ClipboardPlugin",
        "org.opdl.transfer.Plugins.MousePadPlugin.MousePadPlugin",
        "org.opdl.transfer.Plugins.RemoteKeyboardPlugin.RemoteKeyboardPlugin",
        "org.opdl.transfer.Plugins.RunCommandPlugin.RunCommandPlugin"
    ));
    
    /**
     * Check if a plugin should be included in OPDL mode
     */
    public static boolean isPluginAllowed(String pluginClassName) {
        // Exclude explicitly blacklisted plugins
        if (EXCLUDED_PLUGINS.contains(pluginClassName)) {
            return false;
        }
        
        // Include core plugins
        if (CORE_PLUGINS.contains(pluginClassName)) {
            return true;
        }
        
        // For other plugins, include them (they might be custom or new ones)
        return true;
    }
    
    /**
     * Get the set of excluded plugins
     */
    public static Set<String> getExcludedPlugins() {
        return new HashSet<>(EXCLUDED_PLUGINS);
    }
    
    /**
     * Get the set of core plugins
     */
    public static Set<String> getCorePlugins() {
        return new HashSet<>(CORE_PLUGINS);
    }
    
    /**
     * Check if this is a core transfer/plugin functionality
     */
    public static boolean isCoreFunctionality(String pluginClassName) {
        return CORE_PLUGINS.contains(pluginClassName);
    }
    
    /**
     * Check if this is a peripheral/desktop integration feature
     */
    public static boolean isPeripheralFeature(String pluginClassName) {
        return EXCLUDED_PLUGINS.contains(pluginClassName);
    }
}