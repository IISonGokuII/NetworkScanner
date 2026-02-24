package com.mycompany.networkscanner.fragments

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.mycompany.networkscanner.R

class WifiScannerFragment : Fragment() {

    private lateinit var btnScan: MaterialButton
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var resultsText: TextView
    private lateinit var statusText: TextView
    private var wifiManager: WifiManager? = null
    private var receiver: BroadcastReceiver? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_scanner_tool, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val inputLayout1 = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.input_layout_1)
        val inputLayout2 = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.input_layout_2)
        inputLayout1.visibility = View.GONE
        inputLayout2.visibility = View.GONE

        btnScan = view.findViewById(R.id.btn_start)
        val btnClear = view.findViewById<MaterialButton>(R.id.btn_clear)
        progressBar = view.findViewById(R.id.progress_bar)
        resultsText = view.findViewById(R.id.results_text)
        statusText = view.findViewById(R.id.status_text)

        btnScan.text = "WLAN scannen"

        wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        btnScan.setOnClickListener { startScan() }
        btnClear.setOnClickListener {
            resultsText.text = ""
            statusText.text = ""
        }

        // Show current results immediately
        showScanResults()
    }

    private fun startScan() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 200)
            return
        }

        progressBar.visibility = View.VISIBLE
        statusText.text = "Scanne WLANs..."

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                showScanResults()
                try { requireContext().unregisterReceiver(this) } catch (_: Exception) {}
            }
        }

        try {
            requireContext().registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            @Suppress("DEPRECATION")
            wifiManager?.startScan()
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            statusText.text = "Fehler: ${e.message}"
        }
    }

    private fun showScanResults() {
        progressBar.visibility = View.GONE

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            resultsText.text = "Standort-Berechtigung erforderlich für WLAN-Scan."
            return
        }

        try {
            @Suppress("DEPRECATION")
            val results = wifiManager?.scanResults ?: emptyList()
            val sb = StringBuilder()
            sb.appendLine("═══ ${results.size} WLANs gefunden ═══")
            sb.appendLine()

            val sorted = results.sortedByDescending { it.level }
            for ((i, result) in sorted.withIndex()) {
                val ssid = result.SSID.ifEmpty { "(Verstecktes Netzwerk)" }
                val signal = result.level
                val signalBars = when {
                    signal >= -50 -> "████ Excellent"
                    signal >= -60 -> "███░ Gut"
                    signal >= -70 -> "██░░ OK"
                    signal >= -80 -> "█░░░ Schwach"
                    else -> "░░░░ Sehr schwach"
                }
                val freq = result.frequency
                val band = if (freq > 4900) "5 GHz" else "2.4 GHz"
                val security = result.capabilities

                sb.appendLine("${i + 1}. $ssid")
                sb.appendLine("   Signal: $signal dBm [$signalBars]")
                sb.appendLine("   BSSID: ${result.BSSID}")
                sb.appendLine("   Frequenz: $freq MHz ($band)")
                sb.appendLine("   Sicherheit: $security")
                sb.appendLine()
            }

            resultsText.text = sb.toString()
            statusText.text = "${results.size} WLANs in Reichweite"
        } catch (e: Exception) {
            resultsText.text = "Fehler: ${e.message}"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try { receiver?.let { requireContext().unregisterReceiver(it) } } catch (_: Exception) {}
    }
}
