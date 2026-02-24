package com.mycompany.networkscanner.fragments

import androidx.lifecycle.lifecycleScope
import com.mycompany.networkscanner.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TracerouteFragment : BaseScannerFragment() {

    override fun setupUI() {
        inputLayout1.hint = "Hostname oder IP-Adresse"
        input1.setText("google.com")
    }

    override fun onStartClicked() {
        val host = input1.text.toString().trim()
        if (host.isEmpty()) {
            inputLayout1.error = "Bitte Host eingeben"
            return
        }
        inputLayout1.error = null

        resultsText.text = ""
        showProgress(true)
        setStatus("Traceroute zu $host...")
        btnStart.isEnabled = false

        scanJob = lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                NetworkUtils.traceroute(host) { hop ->
                    appendResult(hop)
                }
            }
            if (!isAdded) return@launch
            showProgress(false)
            btnStart.isEnabled = true
            setStatus("Traceroute abgeschlossen")
        }
    }
}
