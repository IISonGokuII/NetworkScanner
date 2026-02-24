package com.mycompany.networkscanner.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.mycompany.networkscanner.R
import com.mycompany.networkscanner.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpeedTestFragment : Fragment() {

    private lateinit var speedValue: TextView
    private lateinit var speedUnit: TextView
    private lateinit var progressCircular: CircularProgressIndicator
    private lateinit var btnStart: MaterialButton
    private lateinit var statusText: TextView
    private lateinit var resultsText: TextView
    private var testJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_speed_test, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        speedValue = view.findViewById(R.id.speed_value)
        speedUnit = view.findViewById(R.id.speed_unit)
        progressCircular = view.findViewById(R.id.progress_circular)
        btnStart = view.findViewById(R.id.btn_start)
        statusText = view.findViewById(R.id.status_text)
        resultsText = view.findViewById(R.id.results_text)

        btnStart.setOnClickListener { startSpeedTest() }
    }

    private fun startSpeedTest() {
        speedValue.text = "0.0"
        resultsText.text = ""
        progressCircular.visibility = View.VISIBLE
        btnStart.isEnabled = false
        statusText.text = "Verbinde mit Testserver..."

        testJob = lifecycleScope.launch {
            // First: Latency test
            statusText.text = "Teste Latenz..."
            val pingResult = withContext(Dispatchers.IO) {
                val start = System.currentTimeMillis()
                try {
                    java.net.InetAddress.getByName("8.8.8.8").isReachable(5000)
                    System.currentTimeMillis() - start
                } catch (_: Exception) { -1L }
            }
            if (!isAdded) return@launch

            // Then: Download speed
            statusText.text = "Teste Download-Geschwindigkeit..."
            val result = withContext(Dispatchers.IO) {
                NetworkUtils.measureDownloadSpeed { speed ->
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            speedValue.text = String.format("%.1f", speed)
                        }
                    }
                }
            }
            if (!isAdded) return@launch

            progressCircular.visibility = View.GONE
            btnStart.isEnabled = true
            speedValue.text = String.format("%.1f", result.downloadSpeedMbps)
            statusText.text = "Test abgeschlossen"

            val sb = StringBuilder()
            sb.appendLine("═══ Ergebnis ═══")
            sb.appendLine()
            sb.appendLine("Download: ${String.format("%.2f", result.downloadSpeedMbps)} Mbps")
            sb.appendLine("Latenz: ${if (pingResult >= 0) "${pingResult}ms" else "N/A"}")
            sb.appendLine("Heruntergeladen: ${result.bytesDownloaded / 1024} KB")
            sb.appendLine("Dauer: ${result.durationMs}ms")
            sb.appendLine()
            sb.appendLine("Bewertung: ${getRating(result.downloadSpeedMbps)}")
            resultsText.text = sb.toString()
        }
    }

    private fun getRating(mbps: Double): String {
        return when {
            mbps >= 100 -> "⚡ Exzellent — Streaming in 4K"
            mbps >= 50 -> "✓ Sehr gut — HD Streaming"
            mbps >= 25 -> "✓ Gut — Standard Streaming"
            mbps >= 10 -> "○ Ausreichend — Surfen OK"
            mbps >= 5 -> "△ Langsam"
            else -> "✗ Sehr langsam"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        testJob?.cancel()
    }
}
