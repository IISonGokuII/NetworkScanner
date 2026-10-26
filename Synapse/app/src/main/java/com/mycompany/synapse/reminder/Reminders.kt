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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Freiwillige, tägliche Erinnerung – nur wenn du heute noch nicht trainiert hast. */
object Reminders {
    private const val CHANNEL = "reminder"
    private const val REQUEST = 4711

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL, "Trainings-Erinnerung", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Eine Erinnerung am Tag, falls du noch nicht trainiert hast"
        }
        nm.createNotificationChannel(channel)
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(context))
        if (!Repo.reminderOn) return
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(Repo.reminderHour, Repo.reminderMinute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, millis, AlarmManager.INTERVAL_DAY, pending(context))
    }

    fun show(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (LocalDate.now() in Repo.trainedDays) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_synapse)
            .setContentTitle("Zeit fürs Gehirntraining")
            .setContentText("${Repo.workoutSize} Spiele, ein paar Minuten – dein Neuronen-Netz wartet.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(1, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        if (Repo.reminderOn) Reminders.show(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Repo.init(context)
        Reminders.schedule(context)
    }
}
