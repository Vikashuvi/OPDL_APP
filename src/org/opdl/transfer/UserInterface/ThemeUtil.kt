/*
 * SPDX-FileCopyrightText: 2018 Philip Cohn-Cort <cliabhach@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
*/
package org.opdl.transfer.UserInterface

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors

/**
 * Utilities for working with android [Themes][android.content.res.Resources.Theme].
 */
object ThemeUtil {
    @Suppress("MemberVisibilityCanBePrivate")
    const val LIGHT_MODE: String = "light"
    @Suppress("MemberVisibilityCanBePrivate")
    const val DARK_MODE: String = "dark"
    const val DEFAULT_MODE: String = "default"

    fun applyTheme(themePref: String) {
        // Always force dark mode for OPDL monochrome B&W theme
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }

    /**
     * Called when an activity is created for the first time to reliably load the correct theme.
     */
    fun setUserPreferredTheme(application: Application) {
        val appTheme = PreferenceManager
            .getDefaultSharedPreferences(application)
            .getString("theme_pref", DEFAULT_MODE)!!
        DynamicColors.applyToActivitiesIfAvailable(application)
        applyTheme(appTheme)
    }
}
