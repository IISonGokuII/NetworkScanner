package com.mycompany.networkscanner.fragments

import androidx.lifecycle.lifecycleScope
import com.mycompany.networkscanner.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DnsLookupFragment : BaseScannerFragment() {

    override fun setupUI() {
        inputLayout1.hint = "Domain oder IP-Adresse"
        input1.setText("google.com")
    }

    override fun onStartClicked() {
        val host = input1.text.toString().trim()
        if (host.isEmpty()) {
            inputLayout1.error = "Bitte Domain eingeben"
            return
        }
        inputLayout1.error = null

        resultsText.text = ""
        showProgress(true)
        setStatus("DNS-Abfrage für $host...")

        scanJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val dns = NetworkUtils.dnsLookup(host)
                val reverse = NetworkUtils.reverseDns(host)
                Pair(dns, reverse)
            }
            if (!isAdded) return@launch
            showProgress(false)

            val (dns, reverse) = result
            val sb = StringBuilder()
            sb.appendLine("═══ DNS Lookup: ${dns.hostname} ═══")
            sb.appendLine()
            sb.appendLine("Kanonischer Name:")
            sb.appendLine("  ${dns.canonicalName}")
            sb.appendLine()
            sb.appendLine("IP-Adressen:")
            for (addr in dns.addresses) {
                sb.appendLine("  → $addr")
            }
            sb.appendLine()
            sb.appendLine("Reverse DNS:")
            sb.appendLine("  $reverse")

            resultsText.text = sb.toString()
            setStatus("${dns.addresses.size} Adressen gefunden")
        }
    }
}
