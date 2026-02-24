package com.mycompany.networkscanner.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mycompany.networkscanner.MainActivity
import com.mycompany.networkscanner.R
import com.mycompany.networkscanner.adapters.ToolItem
import com.mycompany.networkscanner.adapters.ToolsAdapter

class HomeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tools = listOf(
            ToolItem(1, "Geräte-Info", "Netzwerk & IP Details", "i",
                ContextCompat.getColor(requireContext(), R.color.card_info)),
            ToolItem(2, "WLAN Scanner", "WLANs in Reichweite", "W",
                ContextCompat.getColor(requireContext(), R.color.card_wifi)),
            ToolItem(3, "Port Scanner", "Offene Ports finden", "P",
                ContextCompat.getColor(requireContext(), R.color.card_port)),
            ToolItem(4, "Ping", "Host erreichbar?", "◉",
                ContextCompat.getColor(requireContext(), R.color.card_ping)),
            ToolItem(5, "Traceroute", "Route verfolgen", "↗",
                ContextCompat.getColor(requireContext(), R.color.card_trace)),
            ToolItem(6, "DNS Lookup", "DNS Abfragen", "D",
                ContextCompat.getColor(requireContext(), R.color.card_dns)),
            ToolItem(7, "Subnetz Scanner", "Geräte im Netzwerk", "⊞",
                ContextCompat.getColor(requireContext(), R.color.card_subnet)),
            ToolItem(8, "Wake on LAN", "PC aufwecken", "⏻",
                ContextCompat.getColor(requireContext(), R.color.card_wol)),
            ToolItem(9, "Speed Test", "Download-Speed", "⚡",
                ContextCompat.getColor(requireContext(), R.color.card_speed)),
            ToolItem(10, "HTTP Headers", "Header analysieren", "H",
                ContextCompat.getColor(requireContext(), R.color.card_http)),
            ToolItem(11, "Whois", "Domain-Info", "?",
                ContextCompat.getColor(requireContext(), R.color.card_whois)),
            ToolItem(12, "Verbindung", "Netzwerk-Monitor", "◈",
                ContextCompat.getColor(requireContext(), R.color.card_monitor))
        )

        val grid = view.findViewById<RecyclerView>(R.id.tools_grid)
        grid.layoutManager = GridLayoutManager(requireContext(), 3)
        grid.adapter = ToolsAdapter(tools) { tool ->
            val (fragment, title) = when (tool.id) {
                1 -> DeviceInfoFragment() to getString(R.string.device_info)
                2 -> WifiScannerFragment() to getString(R.string.wifi_scanner)
                3 -> PortScannerFragment() to getString(R.string.port_scanner)
                4 -> PingFragment() to getString(R.string.ping_tool)
                5 -> TracerouteFragment() to getString(R.string.traceroute)
                6 -> DnsLookupFragment() to getString(R.string.dns_lookup)
                7 -> SubnetScannerFragment() to getString(R.string.subnet_scanner)
                8 -> WolFragment() to getString(R.string.wol)
                9 -> SpeedTestFragment() to getString(R.string.speed_test)
                10 -> HttpHeaderFragment() to getString(R.string.http_headers)
                11 -> WhoisFragment() to getString(R.string.whois_lookup)
                12 -> DeviceInfoFragment() to getString(R.string.connection_monitor)
                else -> return@ToolsAdapter
            }
            (activity as? MainActivity)?.loadFragment(fragment, title)
        }
    }
}
