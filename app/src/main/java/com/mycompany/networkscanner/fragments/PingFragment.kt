package com.mycompany.networkscanner.fragments

import androidx.lifecycle.lifecycleScope
import com.mycompany.networkscanner.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PingFragment : BaseScannerFragment() {

    override fun setupUI() {
        inputLayout1.hint = "Hostname oder IP-Adresse"
        input1.setText("8.8.8.8")
        inputLayout2.visibility = android.view.View.VISIBLE
        inputLayout2.hint = "Anzahl Pings (Standard: 4)"
        input2.setText("4")
    }

    override fun onStartClicked() {
        val host = input1.text.toString().trim()
        if (host.isEmpty()) {
            inputLayout1.error = "Bitte Host eingeben"
            return
        }
        inputLayout1.error = null
        val count = input2.text.toString().trim().toIntOrNull() ?: 4

        resultsText.text = ""
        showProgress(true)
        setStatus("Pinge $host...")

        scanJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                NetworkUtils.ping(host, count)
            }
            if (!isAdded) return@launch
            showProgress(false)
            resultsText.text = result.output
            setStatus(if (result.success) "✓ Host erreichbar" else "✗ Host nicht erreichbar")
        }
    }
}
