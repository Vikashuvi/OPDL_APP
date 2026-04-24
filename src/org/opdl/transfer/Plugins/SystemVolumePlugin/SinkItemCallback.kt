/*
 * SPDX-FileCopyrightText: 2021 Art Pinch <leonardo906@mail.ru>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
*/
package org.opdl.transfer.Plugins.SystemVolumePlugin

import androidx.recyclerview.widget.DiffUtil

class SinkItemCallback : DiffUtil.ItemCallback<Sink?>() {
    override fun areItemsTheSame(oldItem: Sink, newItem: Sink): Boolean
         = oldItem.name == newItem.name

    override fun areContentsTheSame(oldItem: Sink, newItem: Sink): Boolean
         = oldItem.volume == newItem.volume
        && oldItem.mute == newItem.mute
        && oldItem.isDefault == newItem.isDefault
        && oldItem.maxVolume == newItem.maxVolume // should this be checked?
        && oldItem.description == newItem.description // should this be checked?
}
