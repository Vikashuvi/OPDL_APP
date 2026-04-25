/*
 * SPDX-FileCopyrightText: 2025 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
*/
package org.opdl.transfer.UserInterface.List

import android.view.LayoutInflater
import android.view.View
import org.opdl.transfer.Device
import org.opdl.transfer.databinding.ListItemDeviceEntryBinding

open class DeviceItem @JvmOverloads constructor(
    val device: Device,
    private val callback: ((d: Device) -> Unit),
    private val selectionCallback: ((d: Device, selected: Boolean) -> Unit)? = null,
    private var isMultiSelectMode: Boolean = false
) : ListAdapter.Item
{
    protected lateinit var binding: ListItemDeviceEntryBinding
    private var isSelected: Boolean = false

    override fun inflateView(layoutInflater: LayoutInflater): View {
        binding = ListItemDeviceEntryBinding.inflate(layoutInflater)

        binding.listItemEntryIcon.setImageDrawable(device.icon)
        binding.listItemEntryTitle.text = device.name

        // Only show checkbox if multi-select mode is enabled and selection callback is provided
        if (isMultiSelectMode && selectionCallback != null) {
            binding.listItemEntryCheckbox.visibility = View.VISIBLE
            binding.listItemEntryCheckbox.isChecked = isSelected
            binding.listItemEntryCheckbox.setOnCheckedChangeListener { _, isChecked ->
                isSelected = isChecked
                selectionCallback.invoke(device, isChecked)
            }
        } else {
            binding.listItemEntryCheckbox.visibility = View.GONE
        }

        binding.getRoot().setOnClickListener { v1: View? ->
            if (isMultiSelectMode && selectionCallback != null) {
                binding.listItemEntryCheckbox.isChecked = !binding.listItemEntryCheckbox.isChecked
            } else {
                callback(device)
            }
        }

        return binding.getRoot()
    }

    fun setMultiSelectMode(enabled: Boolean) {
        isMultiSelectMode = enabled
        if (::binding.isInitialized && selectionCallback != null) {
            binding.listItemEntryCheckbox.visibility = if (enabled) View.VISIBLE else View.GONE
        }
    }

    fun setSelected(selected: Boolean) {
        isSelected = selected
        if (::binding.isInitialized) {
            binding.listItemEntryCheckbox.isChecked = selected
        }
    }

    fun getSelected(): Boolean = isSelected
}
