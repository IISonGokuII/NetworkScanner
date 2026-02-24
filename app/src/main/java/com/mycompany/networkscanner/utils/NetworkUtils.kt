package com.mycompany.networkscanner.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.util.Collections

object NetworkUtils {

    // ========== DEVICE INFO ==========
    fun getDeviceIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in Collections.list(interfaces)) {
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "N/A"
                    }
                }
            }
        } catch (_: Exception) {}
        return "N/A"
    }

    fun getNetworkInterfaces(): List<Map<String, String>> {
        val result = mutableListOf<Map<String, String>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in Collections.list(interfaces)) {
                if (intf.isUp) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            result.add(mapOf(
                                "name" to intf.displayName,
                                "ip" to (addr.hostAddress ?: ""),
                                "mac" to (intf.hardwareAddress?.joinToString(":") { String.format("%02X", it) } ?: "N/A"),
                                "mtu" to intf.mtu.toString()
                            ))
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun getConnectionType(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "Nicht verbunden"
        val caps = cm.getNetworkCapabilities(network) ?: return "Unbekannt"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WLAN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobilfunk"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Sonstige"
        }
    }

    fun getWifiInfo(context: Context): Map<String, String> {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wifiManager.connectionInfo
        val dhcp = wifiManager.dhcpInfo
        return mapOf(
            "SSID" to (info.ssid?.replace("\"", "") ?: "N/A"),
            "BSSID" to (info.bssid ?: "N/A"),
            "RSSI" to "${info.rssi} dBm",
            "Frequenz" to "${info.frequency} MHz",
            "Geschwindigkeit" to "${info.linkSpeed} Mbps",
            "Gateway" to intToIp(dhcp.gateway),
            "DNS 1" to intToIp(dhcp.dns1),
            "DNS 2" to intToIp(dhcp.dns2),
            "Subnetzmaske" to intToIp(dhcp.netmask),
            "DHCP Server" to intToIp(dhcp.serverAddress)
        )
    }

    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }

    // ========== PING ==========
    data class PingResult(val output: String, val success: Boolean)

    fun ping(host: String, count: Int = 4): PingResult {
        return try {
            val process = Runtime.getRuntime().exec("ping -c $count -W 2 $host")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.appendLine(line)
            }
            while (errReader.readLine().also { line = it } != null) {
                sb.appendLine(line)
            }
            val exitCode = process.waitFor()
            PingResult(sb.toString(), exitCode == 0)
        } catch (e: Exception) {
            PingResult("Fehler: ${e.message}", false)
        }
    }

    // ========== PORT SCANNER ==========
    data class PortResult(val port: Int, val open: Boolean, val service: String)

    fun scanPort(host: String, port: Int, timeout: Int = 1500): PortResult {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeout)
            socket.close()
            PortResult(port, true, getServiceName(port))
        } catch (_: Exception) {
            PortResult(port, false, "")
        }
    }

    fun getServiceName(port: Int): String {
        return when (port) {
            20 -> "FTP-Data"
            21 -> "FTP"
            22 -> "SSH"
            23 -> "Telnet"
            25 -> "SMTP"
            53 -> "DNS"
            80 -> "HTTP"
            110 -> "POP3"
            143 -> "IMAP"
            443 -> "HTTPS"
            445 -> "SMB"
            993 -> "IMAPS"
            995 -> "POP3S"
            1433 -> "MSSQL"
            1521 -> "Oracle"
            3306 -> "MySQL"
            3389 -> "RDP"
            5432 -> "PostgreSQL"
            5900 -> "VNC"
            6379 -> "Redis"
            8080 -> "HTTP-Alt"
            8443 -> "HTTPS-Alt"
            27017 -> "MongoDB"
            else -> ""
        }
    }

    // ========== TRACEROUTE ==========
    fun traceroute(host: String, onHop: (String) -> Unit) {
        try {
            val process = Runtime.getRuntime().exec("traceroute -m 30 -w 2 $host")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                onHop(line!!)
            }
            while (errReader.readLine().also { line = it } != null) {
                onHop(line!!)
            }
            process.waitFor()
        } catch (e: Exception) {
            onHop("Fehler: ${e.message}")
        }
    }

    // ========== DNS LOOKUP ==========
    data class DnsResult(val hostname: String, val addresses: List<String>, val canonicalName: String)

    fun dnsLookup(host: String): DnsResult {
        return try {
            val addresses = InetAddress.getAllByName(host)
            val ips = addresses.map { it.hostAddress ?: "N/A" }
            val canonical = addresses.firstOrNull()?.canonicalHostName ?: "N/A"
            DnsResult(host, ips, canonical)
        } catch (e: Exception) {
            DnsResult(host, listOf("Auflösung fehlgeschlagen: ${e.message}"), "N/A")
        }
    }

    fun reverseDns(ip: String): String {
        return try {
            val addr = InetAddress.getByName(ip)
            addr.canonicalHostName
        } catch (e: Exception) {
            "Reverse DNS fehlgeschlagen: ${e.message}"
        }
    }

    // ========== SUBNET SCANNER ==========
    fun getSubnetBase(): String {
        val ip = getDeviceIp()
        if (ip == "N/A") return ""
        val parts = ip.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}" else ""
    }

    fun isHostReachable(ip: String, timeout: Int = 1000): Boolean {
        return try {
            InetAddress.getByName(ip).isReachable(timeout)
        } catch (_: Exception) {
            false
        }
    }

    fun getMacFromArp(ip: String): String {
        return try {
            val process = Runtime.getRuntime().exec("cat /proc/net/arp")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.contains(ip)) {
                    val parts = line!!.trim().split("\\s+".toRegex())
                    if (parts.size >= 4) return parts[3].uppercase()
                }
            }
            "N/A"
        } catch (_: Exception) {
            "N/A"
        }
    }

    fun getHostname(ip: String): String {
        return try {
            val addr = InetAddress.getByName(ip)
            val hostname = addr.canonicalHostName
            if (hostname != ip) hostname else "N/A"
        } catch (_: Exception) {
            "N/A"
        }
    }

    // ========== WAKE ON LAN ==========
    fun sendWol(macAddress: String, broadcastIp: String = "255.255.255.255", port: Int = 9): Boolean {
        return try {
            val cleanMac = macAddress.replace("[:-]".toRegex(), "")
            if (cleanMac.length != 12) return false

            val macBytes = ByteArray(6)
            for (i in 0..5) {
                macBytes[i] = cleanMac.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }

            val magicPacket = ByteArray(102)
            for (i in 0..5) magicPacket[i] = 0xFF.toByte()
            for (i in 0..15) {
                System.arraycopy(macBytes, 0, magicPacket, 6 + i * 6, 6)
            }

            val address = InetAddress.getByName(broadcastIp)
            val packet = DatagramPacket(magicPacket, magicPacket.size, address, port)
            val socket = DatagramSocket()
            socket.broadcast = true
            socket.send(packet)
            socket.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    // ========== HTTP HEADERS ==========
    data class HttpHeaderResult(
        val responseCode: Int,
        val responseMessage: String,
        val headers: Map<String, String>,
        val url: String
    )

    fun getHttpHeaders(urlStr: String): HttpHeaderResult {
        var url = urlStr
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = true
            connection.connect()

            val headers = mutableMapOf<String, String>()
            for (i in 0 until connection.headerFields.size) {
                val key = connection.getHeaderFieldKey(i) ?: "Status"
                val value = connection.getHeaderField(i) ?: ""
                headers[key] = value
            }

            val result = HttpHeaderResult(
                connection.responseCode,
                connection.responseMessage ?: "",
                headers,
                connection.url.toString()
            )
            connection.disconnect()
            result
        } catch (e: Exception) {
            HttpHeaderResult(-1, "Fehler: ${e.message}", emptyMap(), url)
        }
    }

    // ========== WHOIS ==========
    fun whoisLookup(domain: String): String {
        return try {
            val cleanDomain = domain.replace("https://", "").replace("http://", "").split("/")[0]
            val socket = Socket()
            socket.connect(InetSocketAddress("whois.iana.org", 43), 10000)
            socket.soTimeout = 10000
            socket.getOutputStream().write("$cleanDomain\r\n".toByteArray())
            val response = socket.getInputStream().bufferedReader().readText()
            socket.close()

            // Try to find the specific whois server
            val referMatch = Regex("refer:\\s+(\\S+)").find(response)
            if (referMatch != null) {
                val whoisServer = referMatch.groupValues[1]
                try {
                    val socket2 = Socket()
                    socket2.connect(InetSocketAddress(whoisServer, 43), 10000)
                    socket2.soTimeout = 10000
                    socket2.getOutputStream().write("$cleanDomain\r\n".toByteArray())
                    val detailedResponse = socket2.getInputStream().bufferedReader().readText()
                    socket2.close()
                    "=== WHOIS Server: $whoisServer ===\n\n$detailedResponse"
                } catch (_: Exception) {
                    "=== IANA WHOIS ===\n\n$response"
                }
            } else {
                response
            }
        } catch (e: Exception) {
            "Whois-Abfrage fehlgeschlagen: ${e.message}"
        }
    }

    // ========== SPEED TEST ==========
    data class SpeedTestResult(
        val downloadSpeedMbps: Double,
        val bytesDownloaded: Long,
        val durationMs: Long
    )

    fun measureDownloadSpeed(onProgress: (Double) -> Unit): SpeedTestResult {
        // Use a commonly available test file
        val testUrls = listOf(
            "http://speedtest.tele2.net/10MB.zip",
            "http://proof.ovh.net/files/10Mb.dat",
            "http://ipv4.download.thinkbroadband.com/10MB.zip"
        )

        for (testUrl in testUrls) {
            try {
                val url = URL(testUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                connection.connect()

                val totalBytes = connection.contentLength.toLong()
                val inputStream = connection.inputStream
                val buffer = ByteArray(8192)
                var bytesRead: Long = 0
                val startTime = System.currentTimeMillis()

                var len: Int
                while (inputStream.read(buffer).also { len = it } != -1) {
                    bytesRead += len
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed > 0) {
                        val speedMbps = (bytesRead * 8.0) / (elapsed * 1000.0)
                        onProgress(speedMbps)
                    }
                }

                inputStream.close()
                connection.disconnect()

                val totalTime = System.currentTimeMillis() - startTime
                val speedMbps = if (totalTime > 0) (bytesRead * 8.0) / (totalTime * 1000.0) else 0.0

                return SpeedTestResult(speedMbps, bytesRead, totalTime)
            } catch (_: Exception) {
                continue
            }
        }

        return SpeedTestResult(0.0, 0, 0)
    }

    // ========== WIFI SCANNER (using system commands) ==========
    fun scanWifiNetworks(): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        try {
            val process = Runtime.getRuntime().exec("dumpsys wifi")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()

            // Parse scan results
            val scanSection = output.substringAfter("Latest scan results:", "")
            if (scanSection.isNotEmpty()) {
                val lines = scanSection.lines()
                for (line in lines) {
                    if (line.contains("SSID:") || line.contains("BSSID:")) {
                        // Basic parsing
                        val ssidMatch = Regex("SSID:\\s*(.+)").find(line)
                        if (ssidMatch != null) {
                            results.add(mapOf("ssid" to ssidMatch.groupValues[1]))
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return results
    }
}
