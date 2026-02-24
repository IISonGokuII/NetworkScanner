package com.mycompany.networkscanner.fragments

import android.view.View
import androidx.lifecycle.lifecycleScope
import com.mycompany.networkscanner.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubnetScannerFragment : BaseScannerFragment() {

    override fun setupUI() {
        val subnet = NetworkUtils.getSubnetBase()
        inputLayout1.hint = "Subnetz-Basis (z.B. 192.168.1)"
        input1.setText(subnet)
        inputLayout2.visibility = View.VISIBLE
        inputLayout2.hint = "Bereich (z.B. 1-254)"
        input2.setText("1-254")
    }

    override fun onStartClicked() {
        val subnet = input1.text.toString().trim()
        if (subnet.isEmpty()) {
            inputLayout1.error = "Bitte Subnetz eingeben"
            return
        }
        inputLayout1.error = null

        val range = input2.text.toString().trim()
        val parts = range.split("-")
        val start = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
        val end = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 254

        resultsText.text = ""
        showProgress(true)
        btnStart.isEnabled = false
        var found = 0

        scanJob = lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                for (i in start..end) {
                    if (!isActive) break
                    val ip = "$subnet.$i"
                    val reachable = NetworkUtils.isHostReachable(ip, 500)
                    if (reachable) {
                        found++
                        val mac = NetworkUtils.getMacFromArp(ip)
                        val hostname = NetworkUtils.getHostname(ip)
                        val hostStr = if (hostname != "N/A") " ($hostname)" else ""
                        appendResult("✓ $ip$hostStr\n  MAC: $mac")
                    }
                    val progress = ((i - start + 1) * 100) / (end - start + 1)
                    setStatus("Scanne $subnet.$i... $progress% — $found Geräte gefunden")
                }
            }
            if (!isAdded) return@launch
            showProgress(false)
            btnStart.isEnabled = true
            setStatus("Fertig: $found Geräte gefunden im Subnetz $subnet")
            if (found == 0) {
                appendResult("Keine Geräte im Bereich $subnet.$start-$end gefunden.")
            }
        }
    }
}
