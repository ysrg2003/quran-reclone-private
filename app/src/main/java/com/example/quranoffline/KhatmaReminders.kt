package com.example.quranoffline

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.quranoffline.data.KhatmaState
import com.example.quranoffline.data.LocalKhatmaStore
import com.example.quranoffline.data.SaveMarkKind
import java.util.Calendar

/**
 * Daily khatma reminders (v20.2). One exact alarm that may fire in Doze, re-armed after every fire, boot,
 * app update, time change and timezone change. Falls back to an inexact idle-allowed alarm if exact alarms are refused.
 */
object KhatmaReminders {
    private const val CHANNEL = "khatma_reminders_v2"

    private fun pending(context: Context, kind: SaveMarkKind): PendingIntent =
        PendingIntent.getBroadcast(
            context, kind.ordinal + 7100,
            Intent(context, KhatmaReminderReceiver::class.java).setAction("com.example.quranoffline.KHATMA_REMINDER").putExtra("kind", kind.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun canUseExact(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun notificationsAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Human status for the screen: what is OK and what is not. */
    fun status(context: Context, s: KhatmaState): String {
        val parts = ArrayList<String>()
        parts += if (notificationsAllowed(context)) "الإشعارات: مسموحة" else "الإشعارات: مغلقة (فعّلها من إعدادات التطبيق)"
        parts += if (canUseExact(context)) "المنبه: دقيق" else "المنبه: غير دقيق (قد يتأخر)"
        parts += if (s.reminderOn) "التذكير: مفعّل ${"%02d".format(s.reminderHour)}:${"%02d".format(s.reminderMinute)}" else "التذكير: متوقف"
        return parts.joinToString(" • ")
    }

    fun nextTrigger(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now + 1000) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

    fun apply(context: Context, s: KhatmaState) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, s.kind)
        am.cancel(pi)
        if (!s.reminderOn) return
        ensureChannel(context)
        val at = nextTrigger(s.reminderHour, s.reminderMinute)
        try {
            if (canUseExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "تذكير الختمة", NotificationManager.IMPORTANCE_HIGH))
    }

    /** Returns false when the notification could not be shown (permission denied or channel blocked). */
    fun notify(context: Context, s: KhatmaState, test: Boolean = false): Boolean {
        ensureChannel(context)
        val what = if (s.kind == SaveMarkKind.HIFZ) "الحفظ والتسميع" else "القراءة والاستماع"
        val text = when {
            test -> "هذا إشعار تجريبي لتذكير $what"
            s.dailyTarget > 0 -> "ورد $what اليوم: ${s.todayCount} من ${s.dailyTarget} آية"
            else -> "حان وقت ورد $what"
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("تذكير الختمة").setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open).setAutoCancel(true).build()
        if (!notificationsAllowed(context)) return false
        return runCatching { NotificationManagerCompat.from(context).notify(7200 + s.kind.ordinal, n) }.isSuccess
    }
}

class KhatmaReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = LocalKhatmaStore(context)
        if (intent.action != "com.example.quranoffline.KHATMA_REMINDER") {
            // boot, app update, time or timezone change: alarms are gone or stale, arm them again
            SaveMarkKind.values().forEach { KhatmaReminders.apply(context, store.load(it)) }
            return
        }
        val kind = runCatching { SaveMarkKind.valueOf(intent.getStringExtra("kind") ?: "") }.getOrNull() ?: return
        val s = store.load(kind)
        if (s.reminderOn) {
            KhatmaReminders.notify(context, s)
            KhatmaReminders.apply(context, s) // next day
        }
    }
}
