package com.nave.screenreview

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.Display
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Runs while capture is on. Shows a permanent notification, and at a random moment
 * every 3-9 minutes takes one screenshot - only when the screen is on and unlocked.
 */
class CaptureService : Service() {

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "stop"
        const val CHANNEL = "capture"
        const val NOTIFY_ID = 1
        const val NOTIFY_STOPPED_ID = 2
        private const val MIN_DELAY = 3 * 60_000L
        private const val MAX_DELAY = 9 * 60_000L
        private const val MAX_WIDTH = 720

        @Volatile
        var running = false
    }

    private val thread = HandlerThread("capture").apply { start() }
    private val handler = Handler(thread.looper)
    private val io = Executors.newSingleThreadExecutor()
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var stoppedByUser = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stoppedByUser = true
            handler.post { projection?.stop() ?: finish(false) }
            return START_NOT_STICKY
        }
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        createChannel()
        // Must be in the foreground (with the capture type) before getting the projection.
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFY_ID, activeNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFY_ID, activeNotification())
        }
        if (data == null || projection != null) {
            if (projection == null) stopSelf()
            return START_NOT_STICKY
        }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val proj = mpm.getMediaProjection(code, data)
        if (proj == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        projection = proj
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                finish(true)
            }
        }, handler)
        val m = metrics()
        display = proj.createVirtualDisplay(
            "screen-review", m.first, m.second, m.third,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, null, null, handler
        )
        running = true
        getSystemService(NotificationManager::class.java).cancel(NOTIFY_STOPPED_ID)
        io.execute { Api.sendEvent("start") }
        scheduleNext()
        return START_NOT_STICKY
    }

    private fun scheduleNext() {
        handler.postDelayed({ tick() }, Random.nextLong(MIN_DELAY, MAX_DELAY + 1))
    }

    /** One random moment: capture if the screen is on and unlocked, otherwise just report in. */
    private fun tick() {
        if (!running) return
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!power.isInteractive || keyguard.isKeyguardLocked) {
            io.execute { Api.sendEvent("screen_off") }
            scheduleNext()
            return
        }
        capture()
    }

    private fun capture() {
        val vd = display ?: return
        val (w, h, dpi) = metrics()
        vd.resize(w, h, dpi)
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        var done = false
        val finishCapture = Runnable {
            if (done) return@Runnable
            done = true
            val image = try { reader.acquireLatestImage() } catch (e: Exception) { null }
            var bitmap: Bitmap? = null
            if (image != null) {
                val plane = image.planes[0]
                val rowPixels = plane.rowStride / plane.pixelStride
                val full = Bitmap.createBitmap(rowPixels, h, Bitmap.Config.ARGB_8888)
                full.copyPixelsFromBuffer(plane.buffer)
                image.close()
                bitmap = Bitmap.createBitmap(full, 0, 0, w, h)
                if (bitmap != full) full.recycle()
            }
            vd.surface = null
            reader.close()
            val takenAt = System.currentTimeMillis()
            if (bitmap != null) {
                io.execute { upload(bitmap, takenAt) }
            } else {
                io.execute { Api.sendEvent("ping") }
            }
            scheduleNext()
        }
        // Wait a moment after the first frame so we don't catch a blank one.
        reader.setOnImageAvailableListener({ handler.postDelayed(finishCapture, 400) }, handler)
        vd.surface = reader.surface
        handler.postDelayed(finishCapture, 3000)
    }

    private fun upload(bitmap: Bitmap, takenAt: Long) {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
        bitmap.recycle()
        val bytes = out.toByteArray()
        val pending = File(filesDir, "pending").apply { mkdirs() }
        if (!Api.sendShot(bytes, takenAt)) {
            // No network: keep it and send later (at most 30 waiting files).
            File(pending, "$takenAt.jpg").writeBytes(bytes)
            pending.listFiles()?.sortedBy { it.name }?.dropLast(30)?.forEach { it.delete() }
            return
        }
        pending.listFiles()?.sortedBy { it.name }?.forEach { f ->
            val t = f.nameWithoutExtension.toLongOrNull() ?: 0L
            if (Api.sendShot(f.readBytes(), t)) f.delete() else return
        }
    }

    /** Screen size scaled down to at most 720 px wide: (width, height, dpi). */
    private fun metrics(): Triple<Int, Int, Int> {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(dm)
        val scale = minOf(1f, MAX_WIDTH.toFloat() / minOf(dm.widthPixels, dm.heightPixels))
        val w = (dm.widthPixels * scale).toInt() and 1.inv()
        val h = (dm.heightPixels * scale).toInt() and 1.inv()
        return Triple(w, h, (dm.densityDpi * scale).toInt().coerceAtLeast(120))
    }

    /** Capture ended (by the app button, the system, or the status-bar stop). */
    private fun finish(report: Boolean) {
        if (!running && projection == null) {
            stopSelf()
            return
        }
        running = false
        handler.removeCallbacksAndMessages(null)
        display?.release()
        display = null
        projection = null
        if (report) io.execute { Api.sendEvent("stop") }
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (!stoppedByUser) {
            getSystemService(NotificationManager::class.java).notify(NOTIFY_STOPPED_ID, stoppedNotification())
        }
        stopSelf()
    }

    override fun onDestroy() {
        if (running) {
            running = false
            projection?.stop()
            io.execute { Api.sendEvent("stop") }
        }
        io.shutdown()
        thread.quitSafely()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "צילום לביקורת", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, CaptureActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun activeNotification(): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("צילום מסך לביקורת פעיל")
            .setContentText("צילום בזמן אקראי כל 3-9 דקות, נשלח לטלפון הצופה")
            .setOngoing(true)
            .setContentIntent(openApp())
            .build()

    private fun stoppedNotification(): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("צילום הביקורת הופסק")
            .setContentText("לחץ כאן כדי להפעיל מחדש")
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
}

/** After a restart Android requires a new approval, so remind and report it. */
class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Prefs.mode(context) != Prefs.MODE_CAPTURE) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CaptureService.CHANNEL, "צילום לביקורת", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, CaptureActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nm.notify(
            CaptureService.NOTIFY_STOPPED_ID,
            Notification.Builder(context, CaptureService.CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("הטלפון הופעל מחדש")
                .setContentText("לחץ כאן כדי להפעיל שוב את צילום הביקורת")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
        val pending = goAsync()
        Thread {
            // The network may need a few seconds after boot.
            repeat(5) {
                if (Api.sendEvent("reboot")) {
                    pending.finish()
                    return@Thread
                }
                Thread.sleep(10_000)
            }
            pending.finish()
        }.start()
    }
}
