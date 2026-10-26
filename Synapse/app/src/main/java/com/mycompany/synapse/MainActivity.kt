package com.mycompany.synapse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mycompany.synapse.data.Repo
import com.mycompany.synapse.ui.SynapseRoot
import com.mycompany.synapse.widget.DailyPuzzleWidget

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SynapseRoot(this) }
    }

    override fun onResume() {
        super.onResume()
        // Neuer Tag → neues Tagestraining; Widget aktualisieren
        Repo.ensureWorkout()
        DailyPuzzleWidget.refreshAll(this)
    }
}
