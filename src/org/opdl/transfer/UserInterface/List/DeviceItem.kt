/*
 * SPDX-FileCopyrightText: 2025 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.opdl.transfer.UserInterface.List

import android.view.LayoutInflater
import android.view.View
import org.opdl.transfer.Device
import org.opdl.transfer.databinding.ListItemDeviceEntryBinding

open class DeviceItem(
    val device: Device,
    private val callback: ((d: Device) -> Unit)
) : ListAdapter.Item
{
    protected lateinit var binding: ListItemDeviceEntryBinding

    override fun inflateView(layoutInflater: LayoutInflater): View {
        binding = ListItemDeviceEntryBinding.inflate(layoutInflater)

        binding.listItemEntryIcon.setImageDrawable(device.icon)
        binding.listItemEntryTitle.text = device.name

        binding.getRoot().setOnClickListener { v1: View? ->
            callback(device)
        }

        return binding.getRoot()
    }
}
