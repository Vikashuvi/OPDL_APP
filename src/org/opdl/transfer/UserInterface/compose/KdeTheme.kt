/*
 * SPDX-FileCopyrightText: 2024 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
 */

package org.opdl.transfer.UserInterface.compose

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Monochrome B&W dark scheme
private val OpdlDarkScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color(0xFF0D0D0D),
    primaryContainer = Color(0xFF222222),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF888888),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2E2E2E),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF555555),
    background = Color(0xFF0D0D0D),
    onBackground = Color.White,
    surface = Color(0xFF1A1A1A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF222222),
    onSurfaceVariant = Color(0xFF888888),
    outline = Color(0xFF3A3A3A),
    error = Color(0xFFEF4444),
    onError = Color.White
)

@Composable
fun KdeTheme(context: Context, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OpdlDarkScheme,
        content = content,
    )
}
