package com.mycompany.networkscanner.fragments

import androidx.lifecycle.lifecycleScope
import com.mycompany.networkscanner.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WhoisFragment : BaseScannerFragment() {

    override fun setupUI() {
        inputLayout1.hint = "Domain (z.B. google.com)"
        input1.setText("google.com")
    }

    override fun onStartClicked() {
        val domain = input1.text.toString().trim()
        if (domain.isEmpty()) {
            inputLayout1.error = "Bitte Domain eingeben"
            return
        }
        inputLayout1.error = null

        resultsText.text = ""
        showProgress(true)
        setStatus("Whois-Abfrage für $domain...")

        scanJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                NetworkUtils.whoisLookup(domain)
            }
            if (!isAdded) return@launch
            showProgress(false)
            resultsText.text = result
            setStatus("Whois-Abfrage abgeschlossen")
        }
    }
}
