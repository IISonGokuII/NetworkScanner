package com.mycompany.synapse.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.mycompany.synapse.MainActivity
import com.mycompany.synapse.R
import com.mycompany.synapse.data.Repo
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Freiwillige Erinnerungen: täglich (nur wenn heute noch nicht trainiert) und sonntags der Wochenrückblick.
 * Einzelalarme mit setAndAllowWhileIdle – sie kommen auch im Stromsparmodus zuverlässig, der Empfänger plant den nächsten.
 */
object Reminders {
    private const val CHANNEL = "reminder"
    private const val REQ_DAILY = 4711
    private const val REQ_WEEKLY = 4712
    const val ACTION_DAILY = "com.mycompany.synapse.DAILY"
    const val ACTION_WEEKLY = "com.mycompany.synapse.WEEKLY"
    const val EXTRA_REVIEW_WEEK = "review_week"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL, "Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Tägliche Trainings-Erinnerung und Wochenrückblick"
        }
        nm.createNotificationChannel(channel)
    }

    private fun pending(context: Context, action: String, req: Int): PendingIntent = PendingIntent.getBroadcast(
        context, req, Intent(context, ReminderReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun at(time: LocalDateTime) = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Plant beide Erinnerungen entsprechend der Einstellungen neu. */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val daily = pending(context, ACTION_DAILY, REQ_DAILY)
        val weekly = pending(context, ACTION_WEEKLY, REQ_WEEKLY)
        am.cancel(daily)
        am.cancel(weekly)
        val now = LocalDateTime.now()
        if (Repo.reminderOn) {
            var next = now.toLocalDate().atTime(Repo.reminderHour, Repo.reminderMinute)
            if (!next.isAfter(now)) next = next.plusDays(1)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at(next), daily)
        }
        if (Repo.reviewNotifOn) {
            var next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(19, 0)
            if (!next.isAfter(now)) next = next.plusWeeks(1)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at(next), weekly)
        }
    }

    private fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(context: Context, id: Int, title: String, text: String, extras: Intent.() -> Unit = {}) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_synapse)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(id, n)
    }

    fun showDaily(context: Context) {
        if (LocalDate.now() in Repo.trainedDays) return
        post(context, 1, "Zeit fürs Gehirntraining", "${Repo.workoutSize} Spiele, ein paar Minuten – dein Neuronen-Netz wartet.")
    }

    fun showWeekly(context: Context) {
        val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = Repo.trainedDays.count { !it.isBefore(weekStart) }
        post(
            context, 2, "Dein Wochenrückblick",
            "$days von ${Repo.weeklyGoal} Tagen trainiert" + if (days >= Repo.weeklyGoal) " – Wochenziel geschafft!" else ". Schau dir an, was diese Woche besser wurde.",
        ) { putExtra(EXTRA_REVIEW_WEEK, weekStart.toEpochDay()) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        when (intent.action) {
            Reminders.ACTION_DAILY -> if (Repo.reminderOn) Reminders.showDaily(context)
            Reminders.ACTION_WEEKLY -> if (Repo.reviewNotifOn) Reminders.showWeekly(context)
        }
        Reminders.schedule(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Repo.init(context)
        Reminders.schedule(context)
    }
}
