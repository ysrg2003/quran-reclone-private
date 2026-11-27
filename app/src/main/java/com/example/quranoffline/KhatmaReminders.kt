package com.example.quranoffline

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.quranoffline.data.KhatmaState
import com.example.quranoffline.data.LocalKhatmaStore
import com.example.quranoffline.data.SaveMarkKind
import java.util.Calendar

/**
 * Daily khatma reminders. One exact alarm that may fire in Doze, re-armed after every fire, boot,
 * app update, time change and timezone change. Optional repeats (interval and count chosen by the user) while today's wird is not done.
 * The notification plays one short frame-drum hit (bundled, synthesized for this app).
 */
object KhatmaReminders {
    private const val CHANNEL_PREFIX = "khatma_reminders_duff_"
    private val LEGACY_CHANNELS = listOf("khatma_reminders_v3")

    /** Number of duff hits in the reminder sound (1..4, default 2). One sound file and one channel per count: a channel's sound cannot change after creation. */
    fun duffHits(context: Context): Int =
        context.getSharedPreferences("quran_reminder_sound", Context.MODE_PRIVATE).getInt("duff_hits", 2).coerceIn(1, 4)

    fun setDuffHits(context: Context, hits: Int) {
        context.getSharedPreferences("quran_reminder_sound", Context.MODE_PRIVATE).edit().putInt("duff_hits", hits.coerceIn(1, 4)).apply()
    }

    private fun soundRes(hits: Int) = when (hits) { 1 -> R.raw.khatma_duff_1; 3 -> R.raw.khatma_duff_3; 4 -> R.raw.khatma_duff_4; else -> R.raw.khatma_duff_2 }
    private fun channelId(hits: Int) = CHANNEL_PREFIX + hits

    private fun pending(context: Context, kind: SaveMarkKind, repeatNo: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, (if (repeatNo == 0) 7100 else 7300) + kind.ordinal,
            Intent(context, KhatmaReminderReceiver::class.java).setAction("com.example.quranoffline.KHATMA_REMINDER")
                .putExtra("kind", kind.name).putExtra("repeat", repeatNo),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun canUseExact(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun notificationsAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

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

    private fun arm(context: Context, at: Long, pi: PendingIntent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (canUseExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Re-arms the daily alarm and drops any pending repeat. */
    fun apply(context: Context, s: KhatmaState) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, s.kind, 0))
        am.cancel(pending(context, s.kind, 1))
        if (!s.reminderOn) return
        ensureChannel(context)
        arm(context, nextTrigger(s.reminderHour, s.reminderMinute), pending(context, s.kind, 0))
    }

    fun scheduleRepeat(context: Context, s: KhatmaState, nextRepeatNo: Int) {
        if (s.repeatMinutes <= 0 || nextRepeatNo > s.repeatCount.coerceIn(1, 50)) return
        arm(context, System.currentTimeMillis() + s.repeatMinutes * 60_000L, pending(context, s.kind, nextRepeatNo))
    }

    private fun ensureChannel(context: Context): String {
        val hits = duffHits(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sound = Uri.parse("android.resource://${context.packageName}/${soundRes(hits)}")
        val ch = NotificationChannel(channelId(hits), "تذكير الختمة (دف $hits)", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
        }
        nm.createNotificationChannel(ch)
        // Only the channel for the chosen hit count stays; the old v3 channel and the other counts are removed.
        (LEGACY_CHANNELS + (1..4).filter { it != hits }.map(::channelId)).forEach { id -> if (nm.getNotificationChannel(id) != null) nm.deleteNotificationChannel(id) }
        return channelId(hits)
    }

    /** Returns false when the notification could not be shown (permission denied or channel blocked). */
    fun notify(context: Context, s: KhatmaState, test: Boolean = false): Boolean {
        val channel = ensureChannel(context)
        val what = if (s.kind == SaveMarkKind.HIFZ) "الحفظ والتسميع" else "القراءة والاستماع"
        val text = when {
            test -> "هذا إشعار تجريبي لتذكير $what"
            s.targetAyahs > 0 -> "ورد $what اليوم: ${s.todayCount} من ${s.targetAyahs} آية"
            else -> "حان وقت ورد $what"
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("تذكير الختمة").setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(Uri.parse("android.resource://${context.packageName}/${soundRes(duffHits(context))}"))
            .setContentIntent(open).setAutoCancel(true).build()
        if (!notificationsAllowed(context)) return false
        return runCatching { NotificationManagerCompat.from(context).notify(7200 + s.kind.ordinal, n) }.isSuccess
    }
}

class KhatmaReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = LocalKhatmaStore(context)
        if (intent.action != "com.example.quranoffline.KHATMA_REMINDER") {
            SaveMarkKind.values().forEach { KhatmaReminders.apply(context, store.load(it)) }
            return
        }
        val kind = runCatching { SaveMarkKind.valueOf(intent.getStringExtra("kind") ?: "") }.getOrNull() ?: return
        val repeatNo = intent.getIntExtra("repeat", 0)
        val s = store.load(kind)
        if (!s.reminderOn) return
        if (repeatNo == 0) {
            KhatmaReminders.notify(context, s)
            KhatmaReminders.apply(context, s) // tomorrow
            KhatmaReminders.scheduleRepeat(context, s, 1)
        } else if (!s.wirdDone) {
            KhatmaReminders.notify(context, s)
            KhatmaReminders.scheduleRepeat(context, s, repeatNo + 1)
        }
    }
}
