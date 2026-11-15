package com.nave.screenreview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

/** Downloads screenshots once (kept on disk) and shows them scaled to the view. */
object Images {
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val memory = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun file(ctx: Context, time: Long) = File(File(ctx.cacheDir, "shots").apply { mkdirs() }, "$time.jpg")

    /** Blocking: bitmap of [time] at most [maxPx] on its long side, or null. */
    fun loadNow(ctx: Context, time: Long, maxPx: Int): Bitmap? {
        val key = "$time/$maxPx"
        memory.get(key)?.let { return it }
        val f = file(ctx, time)
        if (!f.exists()) {
            val bytes = Api.getImage(time) ?: return null
            f.writeBytes(bytes)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        memory.put(key, bmp)
        return bmp
    }

    fun show(view: ImageView, time: Long, maxPx: Int) {
        view.tag = time
        val key = "$time/$maxPx"
        val cached = memory.get(key)
        if (cached != null) {
            view.setImageBitmap(cached)
            return
        }
        view.setImageDrawable(null)
        val ctx = view.context.applicationContext
        pool.execute {
            val bmp = loadNow(ctx, time, maxPx)
            main.post {
                if (view.tag == time && bmp != null) view.setImageBitmap(bmp)
            }
        }
    }

    /** Remove downloaded files older than two days. */
    fun cleanup(ctx: Context) {
        val limit = System.currentTimeMillis() - 48 * 3600_000L
        File(ctx.cacheDir, "shots").listFiles()?.forEach { f ->
            val t = f.nameWithoutExtension.toLongOrNull() ?: 0L
            if (t < limit) f.delete()
        }
    }
}
