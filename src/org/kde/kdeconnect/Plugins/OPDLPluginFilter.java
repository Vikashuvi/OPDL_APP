/*
 * SPDX-FileCopyrightText: 2025 OPDL Project Team
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
 */
package org.kde.kdeconnect.Plugins;

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
        "org.kde.kdeconnect.Plugins.NotificationsPlugin.NotificationsPlugin",
        "org.kde.kdeconnect.Plugins.ReceiveNotificationsPlugin.ReceiveNotificationsPlugin",
        "org.kde.kdeconnect.Plugins.TelephonyPlugin.TelephonyPlugin",
        "org.kde.kdeconnect.Plugins.SMSPlugin.SMSPlugin",
        "org.kde.kdeconnect.Plugins.ContactsPlugin.ContactsPlugin",
        "org.kde.kdeconnect.Plugins.MprisPlugin.MprisPlugin",
        "org.kde.kdeconnect.Plugins.MprisReceiverPlugin.MprisReceiverPlugin",
        "org.kde.kdeconnect.Plugins.PresenterPlugin.PresenterPlugin",
        "org.kde.kdeconnect.Plugins.FindMyPhonePlugin.FindMyPhonePlugin",
        "org.kde.kdeconnect.Plugins.FindRemoteDevicePlugin.FindRemoteDevicePlugin",
        "org.kde.kdeconnect.Plugins.SystemVolumePlugin.SystemVolumePlugin",
        "org.kde.kdeconnect.Plugins.BatteryPlugin.BatteryPlugin",
        "org.kde.kdeconnect.Plugins.ConnectivityReportPlugin.ConnectivityReportPlugin"
    ));
    
    // Core plugins that should always be included
    private static final Set<String> CORE_PLUGINS = new HashSet<>(Arrays.asList(
        "org.kde.kdeconnect.Plugins.SharePlugin.SharePlugin",
        "org.kde.kdeconnect.Plugins.PingPlugin.PingPlugin",
        "org.kde.kdeconnect.Plugins.SftpPlugin.SftpPlugin",
        "org.kde.kdeconnect.Plugins.ClipboardPlugin.ClipboardPlugin",
        "org.kde.kdeconnect.Plugins.MousePadPlugin.MousePadPlugin",
        "org.kde.kdeconnect.Plugins.RemoteKeyboardPlugin.RemoteKeyboardPlugin",
        "org.kde.kdeconnect.Plugins.RunCommandPlugin.RunCommandPlugin"
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