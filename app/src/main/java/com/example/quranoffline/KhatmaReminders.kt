package com.example.quranoffline

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.quranoffline.data.KhatmaState
import com.example.quranoffline.data.LocalKhatmaStore
import com.example.quranoffline.data.SaveMarkKind
import java.util.Calendar

/** Daily khatma reminders. Inexact repeating alarm: no exact-alarm permission, may drift a few minutes. */
object KhatmaReminders {
    private const val CHANNEL = "khatma_reminders"

    private fun pending(context: Context, kind: SaveMarkKind): PendingIntent =
        PendingIntent.getBroadcast(
            context, kind.ordinal + 7100,
            Intent(context, KhatmaReminderReceiver::class.java).putExtra("kind", kind.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun apply(context: Context, s: KhatmaState) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, s.kind)
        am.cancel(pi)
        if (!s.reminderOn) return
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, s.reminderHour); set(Calendar.MINUTE, s.reminderMinute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, c.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }

    fun notify(context: Context, s: KhatmaState) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "تذكير الختمة", NotificationManager.IMPORTANCE_DEFAULT))
        val what = if (s.kind == SaveMarkKind.HIFZ) "الحفظ والتسميع" else "القراءة والاستماع"
        val text = if (s.dailyTarget > 0) "ورد $what اليوم: ${s.todayCount} من ${s.dailyTarget} آية" else "حان وقت ورد $what"
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("تذكير الختمة").setContentText(text)
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { nm.notify(7200 + s.kind.ordinal, n) } // fails silently if the user denied notifications
    }
}

class KhatmaReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = LocalKhatmaStore(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SaveMarkKind.values().forEach { KhatmaReminders.apply(context, store.load(it)) }
            return
        }
        val kind = runCatching { SaveMarkKind.valueOf(intent.getStringExtra("kind") ?: "") }.getOrNull() ?: return
        val s = store.load(kind)
        if (s.reminderOn) KhatmaReminders.notify(context, s)
    }
}
