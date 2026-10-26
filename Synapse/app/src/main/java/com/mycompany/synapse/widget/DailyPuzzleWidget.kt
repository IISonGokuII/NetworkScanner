package com.mycompany.synapse.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mycompany.synapse.MainActivity
import com.mycompany.synapse.R
import com.mycompany.synapse.game.logic.SequenceGen
import java.time.LocalDate
import kotlin.random.Random

/** Startbildschirm-Widget: jeden Tag eine neue Zahlenfolge. Antippen zeigt die Lösung. */
class DailyPuzzleWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, views(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            val prefs = context.getSharedPreferences("synapse_widget", Context.MODE_PRIVATE)
            val today = LocalDate.now().toEpochDay()
            val revealed = prefs.getLong("revealed_day", -1L) == today
            prefs.edit().putLong("revealed_day", if (revealed) -1L else today).apply()
            refreshAll(context)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.mycompany.synapse.widget.TOGGLE"

        fun puzzleFor(day: LocalDate) =
            SequenceGen.generate(14 + (day.toEpochDay() % 20).toInt(), Random(day.toEpochDay() * 104729 + 3))

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DailyPuzzleWidget::class.java))
            for (id in ids) manager.updateAppWidget(id, views(context))
        }

        private fun views(context: Context): RemoteViews {
            val today = LocalDate.now()
            val task = puzzleFor(today)
            val revealed = context.getSharedPreferences("synapse_widget", Context.MODE_PRIVATE)
                .getLong("revealed_day", -1L) == today.toEpochDay()
            val v = RemoteViews(context.packageName, R.layout.widget_daily_puzzle)
            v.setTextViewText(R.id.widget_puzzle, task.shown.joinToString(", ") + ", ?")
            v.setTextViewText(
                R.id.widget_answer,
                if (revealed) "Lösung: ${task.answer} – ${task.rule}" else "Tippen für die Lösung",
            )
            val toggle = PendingIntent.getBroadcast(
                context, 1, Intent(context, DailyPuzzleWidget::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.widget_answer, toggle)
            v.setOnClickPendingIntent(R.id.widget_puzzle, toggle)
            val open = PendingIntent.getActivity(
                context, 2, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.widget_train, open)
            return v
        }
    }
}
