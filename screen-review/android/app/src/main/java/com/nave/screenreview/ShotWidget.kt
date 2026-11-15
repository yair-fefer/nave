package com.nave.screenreview

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews

/** Home-screen widget: the latest screenshot and how long ago it was taken. */
class ShotWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        scheduleRefresh(context)
        val pending = goAsync()
        Thread {
            try {
                updateAll(context)
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
    }

    companion object {
        private const val REFRESH_MS = 15 * 60_000L

        private fun refreshIntent(ctx: Context): PendingIntent {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ShotWidget::class.java))
            val i = Intent(ctx, ShotWidget::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            return PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        /** Refresh about every 15 minutes while the phone is awake (no wake-ups at night). */
        private fun scheduleRefresh(ctx: Context) {
            ctx.getSystemService(AlarmManager::class.java).setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + REFRESH_MS,
                REFRESH_MS,
                refreshIntent(ctx)
            )
        }

        /** Blocking: fetch the latest screenshot and redraw every widget. */
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ShotWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(ctx.packageName, R.layout.widget_shot)
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            val feed = Api.getFeed(24)
            val newest = feed?.shots?.firstOrNull()
            when {
                feed == null -> views.setTextViewText(R.id.widget_text, "אין חיבור")
                newest == null -> {
                    views.setImageViewBitmap(R.id.widget_image, null)
                    views.setTextViewText(R.id.widget_text, "אין צילומים היום")
                }
                else -> {
                    val bmp = Images.loadNow(ctx, newest, 600)
                    if (bmp != null) views.setImageViewBitmap(R.id.widget_image, bmp)
                    var text = Fmt.clock(newest) + "  " + Fmt.ago(newest, feed.now)
                    if (feed.lastSeenState == "stop" || feed.lastSeenState == "reboot") text = "הצילום כבוי!  " + text
                    views.setTextViewText(R.id.widget_text, text)
                }
            }
            mgr.updateAppWidget(ids, views)
        }
    }
}
