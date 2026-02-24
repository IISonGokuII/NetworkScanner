package com.mycompany.networkscanner.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.mycompany.networkscanner.R
import com.mycompany.networkscanner.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceInfoFragment : Fragment() {

    private lateinit var container: LinearLayout
    private lateinit var btnRefresh: MaterialButton
    private lateinit var progressBar: LinearProgressIndicator

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_device_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view.findViewById(R.id.info_container)
        btnRefresh = view.findViewById(R.id.btn_refresh)
        progressBar = view.findViewById(R.id.progress_bar)

        btnRefresh.setOnClickListener { loadInfo() }
        loadInfo()
    }

    private fun loadInfo() {
        progressBar.visibility = View.VISIBLE
        // Remove old info cards (keep button and progress)
        while (container.childCount > 2) {
            container.removeViewAt(2)
        }

        lifecycleScope.launch {
            val infos = withContext(Dispatchers.IO) { gatherInfo() }
            if (!isAdded) return@launch
            progressBar.visibility = View.GONE

            for ((label, value) in infos) {
                addInfoCard(label, value)
            }
        }
    }

    private fun gatherInfo(): List<Pair<String, String>> {
        val infos = mutableListOf<Pair<String, String>>()

        infos.add("Geräte-IP" to NetworkUtils.getDeviceIp())
        infos.add("Verbindungstyp" to NetworkUtils.getConnectionType(requireContext()))

        // Network interfaces
        val interfaces = NetworkUtils.getNetworkInterfaces()
        for (intf in interfaces) {
            infos.add("Interface: ${intf["name"]}" to
                "IP: ${intf["ip"]}\nMAC: ${intf["mac"]}\nMTU: ${intf["mtu"]}")
        }

        // WiFi details
        try {
            val wifi = NetworkUtils.getWifiInfo(requireContext())
            for ((key, value) in wifi) {
                infos.add(key to value)
            }
        } catch (_: Exception) {
            infos.add("WLAN" to "Nicht verfügbar")
        }

        // Public IP
        try {
            val publicIp = java.net.URL("https://api.ipify.org").readText()
            infos.add("Öffentliche IP" to publicIp)
        } catch (_: Exception) {
            infos.add("Öffentliche IP" to "Nicht ermittelbar")
        }

        infos.add("Android Version" to android.os.Build.VERSION.RELEASE)
        infos.add("Gerät" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")

        return infos
    }

    private fun addInfoCard(label: String, value: String) {
        val cardView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_info_card, container, false)
        cardView.findViewById<TextView>(R.id.info_label).text = label
        cardView.findViewById<TextView>(R.id.info_value).text = value
        container.addView(cardView)
    }
}
