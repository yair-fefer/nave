package com.nave.screenreview

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Prefs {
    const val MODE_CAPTURE = "capture"
    const val MODE_VIEWER = "viewer"

    fun mode(ctx: Context): String? =
        ctx.getSharedPreferences("app", Context.MODE_PRIVATE).getString("mode", null)

    fun setMode(ctx: Context, mode: String) {
        ctx.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putString("mode", mode).apply()
    }
}

object Fmt {
    fun clock(time: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))

    fun dayClock(time: Long): String =
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(time))

    /** "לפני 5 דק'" style text. */
    fun ago(time: Long, now: Long = System.currentTimeMillis()): String {
        val min = ((now - time) / 60_000).coerceAtLeast(0)
        return when {
            min < 1 -> "עכשיו"
            min < 60 -> "לפני $min דק'"
            min < 48 * 60 -> "לפני ${min / 60} שע'"
            else -> "לפני ${min / 1440} ימים"
        }
    }

    fun state(type: String): String = when (type) {
        "shot" -> "פעיל (נשלח צילום)"
        "ping" -> "פעיל"
        "screen_off" -> "פעיל, המסך כבוי"
        "start" -> "הופעל"
        "stop" -> "הצילום הופסק"
        "reboot" -> "הטלפון הופעל מחדש, הצילום עוד לא הופעל"
        else -> "לא ידוע"
    }
}
