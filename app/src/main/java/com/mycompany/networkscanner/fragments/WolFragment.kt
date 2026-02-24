package com.mycompany.networkscanner.fragments

import android.view.View
import androidx.lifecycle.lifecycleScope
import com.mycompany.networkscanner.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WolFragment : BaseScannerFragment() {

    override fun setupUI() {
        inputLayout1.hint = "MAC-Adresse (z.B. AA:BB:CC:DD:EE:FF)"
        input1.setText("")
        inputLayout2.visibility = View.VISIBLE
        inputLayout2.hint = "Broadcast-IP (Standard: 255.255.255.255)"
        input2.setText("255.255.255.255")
        btnStart.text = "Magic Packet senden"
    }

    override fun onStartClicked() {
        val mac = input1.text.toString().trim()
        if (mac.isEmpty()) {
            inputLayout1.error = "Bitte MAC-Adresse eingeben"
            return
        }
        inputLayout1.error = null
        val broadcast = input2.text.toString().trim().ifEmpty { "255.255.255.255" }

        resultsText.text = ""
        showProgress(true)
        setStatus("Sende Magic Packet...")

        scanJob = lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                NetworkUtils.sendWol(mac, broadcast)
            }
            if (!isAdded) return@launch
            showProgress(false)

            if (success) {
                resultsText.text = buildString {
                    appendLine("✓ Magic Packet erfolgreich gesendet!")
                    appendLine()
                    appendLine("Ziel-MAC: $mac")
                    appendLine("Broadcast: $broadcast")
                    appendLine("Port: 9")
                    appendLine()
                    appendLine("Hinweis: Das Gerät muss WoL")
                    appendLine("unterstützen und aktiviert haben.")
                }
                setStatus("Magic Packet gesendet")
            } else {
                resultsText.text = "✗ Fehler beim Senden des Magic Packets.\nÜberprüfe die MAC-Adresse."
                setStatus("Fehler")
            }
        }
    }
}
