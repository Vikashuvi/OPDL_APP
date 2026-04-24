/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
*/
package org.opdl.transfer.UserInterface.List

import android.view.LayoutInflater
import android.view.View
import org.opdl.transfer.databinding.ListItemEntryBinding

open class EntryItem protected constructor(protected val title: String, protected val subtitle: String?) : ListAdapter.Item {

    override fun inflateView(layoutInflater: LayoutInflater): View {
        val binding = ListItemEntryBinding.inflate(layoutInflater)

        binding.listItemEntryTitle.text = title

        if (subtitle != null) {
            binding.listItemEntrySummary.visibility = View.VISIBLE
            binding.listItemEntrySummary.text = subtitle
        }

        return binding.root
    }
}
