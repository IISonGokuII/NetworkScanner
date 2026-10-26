package com.mycompany.synapse

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mycompany.synapse.data.Repo
import com.mycompany.synapse.reminder.Reminders
import com.mycompany.synapse.ui.DeepLink
import com.mycompany.synapse.ui.Screen
import com.mycompany.synapse.ui.SynapseRoot
import com.mycompany.synapse.widget.DailyPuzzleWidget

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent { SynapseRoot(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Benachrichtigung „Wochenrückblick“ öffnet direkt den Rückblick. */
    private fun handle(intent: Intent?) {
        val week = intent?.getLongExtra(Reminders.EXTRA_REVIEW_WEEK, Long.MIN_VALUE) ?: Long.MIN_VALUE
        if (week != Long.MIN_VALUE) DeepLink.pending = Screen.WeekReviewScreen(week)
    }

    override fun onResume() {
        super.onResume()
        // Neuer Tag → neues Tagestraining; Widget aktualisieren; ggf. automatische Sicherung
        Repo.ensureWorkout()
        DailyPuzzleWidget.refreshAll(this)
        Repo.autoBackupIfDue(this)
    }
}
