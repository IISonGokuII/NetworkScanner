package com.mycompany.networkscanner.fragments

import androidx.lifecycle.lifecycleScope
import com.mycompany.networkscanner.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HttpHeaderFragment : BaseScannerFragment() {

    override fun setupUI() {
        inputLayout1.hint = "URL (z.B. google.com)"
        input1.setText("google.com")
    }

    override fun onStartClicked() {
        val url = input1.text.toString().trim()
        if (url.isEmpty()) {
            inputLayout1.error = "Bitte URL eingeben"
            return
        }
        inputLayout1.error = null

        resultsText.text = ""
        showProgress(true)
        setStatus("Rufe HTTP-Header ab...")

        scanJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                NetworkUtils.getHttpHeaders(url)
            }
            if (!isAdded) return@launch
            showProgress(false)

            val sb = StringBuilder()
            if (result.responseCode > 0) {
                sb.appendLine("═══ HTTP Response ═══")
                sb.appendLine()
                sb.appendLine("URL: ${result.url}")
                sb.appendLine("Status: ${result.responseCode} ${result.responseMessage}")
                sb.appendLine()
                sb.appendLine("═══ Headers ═══")
                sb.appendLine()
                for ((key, value) in result.headers) {
                    sb.appendLine("$key: $value")
                }
                setStatus("HTTP ${result.responseCode} — ${result.headers.size} Header")
            } else {
                sb.appendLine("✗ ${result.responseMessage}")
                setStatus("Fehler bei der Abfrage")
            }
            resultsText.text = sb.toString()
        }
    }
}
