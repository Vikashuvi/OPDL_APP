/*
 * SPDX-FileCopyrightText: 2025 Tharun
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.UserInterface

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.kde.kdeconnect.base.BaseFragment
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.FragmentSpeedMetricsBinding

class SpeedMetricsFragment : BaseFragment<FragmentSpeedMetricsBinding>() {

    override fun getActionBarTitle(): String? = getString(R.string.speed_metrics)

    override fun onInflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): FragmentSpeedMetricsBinding {
        return FragmentSpeedMetricsBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.startTestButton.setOnClickListener {
            // Show loading state
            binding.speedMetricsProgress.visibility = View.VISIBLE
            binding.speedMetricsResult.text = getString(R.string.speed_metrics_running)

            val (latencyMs, throughput, isGbit) = if (binding.opdlModeToggle.isChecked) {
                // OPDL enabled: random high-speed result between 2.4 and 2.7 Gbit/s
                val randomThroughputGbit = 2.4 + Math.random() * 0.3
                Triple(2, randomThroughputGbit, true)
            } else {
                // Normal fake metrics: random 100–150 Mbit/s
                val randomThroughputMbit = 100.0 + Math.random() * 50.0
                Triple(25, randomThroughputMbit, false)
            }

            // TODO: Replace this fake result with real speed metrics (ping/throughput) logic.
            view.postDelayed({
                binding.speedMetricsProgress.visibility = View.GONE
                val formatRes = if (isGbit) {
                    R.string.speed_metrics_result_format_gbit
                } else {
                    R.string.speed_metrics_result_format_mbit
                }
                binding.speedMetricsResult.text = getString(
                    formatRes,
                    latencyMs,
                    throughput
                )
            }, 1500)
        }
    }
}
