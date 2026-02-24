package com.mycompany.networkscanner.fragments

import android.view.View
import androidx.lifecycle.lifecycleScope
import com.mycompany.networkscanner.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PortScannerFragment : BaseScannerFragment() {

    override fun setupUI() {
        inputLayout1.hint = "Hostname oder IP-Adresse"
        input1.setText("")
        inputLayout2.visibility = View.VISIBLE
        inputLayout2.hint = "Port-Bereich (z.B. 1-1024)"
        input2.setText("1-1024")
    }

    override fun onStartClicked() {
        val host = input1.text.toString().trim()
        if (host.isEmpty()) {
            inputLayout1.error = "Bitte Host eingeben"
            return
        }
        inputLayout1.error = null

        val range = input2.text.toString().trim()
        val parts = range.split("-")
        val startPort = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 1
        val endPort = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1024

        resultsText.text = ""
        showProgress(true)
        setStatus("Scanne Ports $startPort-$endPort auf $host...")
        btnStart.isEnabled = false

        var openCount = 0
        val total = endPort - startPort + 1

        scanJob = lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                for (port in startPort..endPort) {
                    if (!isActive) break
                    val result = NetworkUtils.scanPort(host, port, 800)
                    if (result.open) {
                        openCount++
                        val service = if (result.service.isNotEmpty()) " (${result.service})" else ""
                        appendResult("✓ Port $port OFFEN$service")
                    }
                    val progress = ((port - startPort + 1) * 100) / total
                    setStatus("Scanne... $progress% ($port/$endPort) — $openCount offen")
                }
            }
            if (!isAdded) return@launch
            showProgress(false)
            btnStart.isEnabled = true
            setStatus("Fertig: $openCount offene Ports gefunden ($startPort-$endPort)")
            if (openCount == 0) {
                appendResult("Keine offenen Ports im Bereich $startPort-$endPort gefunden.")
            }
        }
    }
}
