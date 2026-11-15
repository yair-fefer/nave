package com.nave.screenreview

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Talks to the small server: send screenshots/events, read the feed and images. */
object Api {

    private fun open(path: String, method: String): HttpURLConnection {
        val conn = URL(BuildConfig.SERVER_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("x-key", BuildConfig.APP_KEY)
        return conn
    }

    /** Uploads one JPEG. Returns true on success. */
    fun sendShot(jpeg: ByteArray, takenAt: Long): Boolean {
        return try {
            val conn = open("/api/shot", "POST")
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "image/jpeg")
            conn.setRequestProperty("x-time", takenAt.toString())
            conn.setFixedLengthStreamingMode(jpeg.size)
            conn.outputStream.use { it.write(jpeg) }
            val ok = conn.responseCode == 200
            conn.disconnect()
            ok
        } catch (e: Exception) {
            false
        }
    }

    /** Sends a status event: ping, screen_off, start, stop, reboot. */
    fun sendEvent(type: String): Boolean {
        return try {
            val conn = open("/api/event?type=$type", "POST")
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(0)
            conn.outputStream.close()
            val ok = conn.responseCode == 200
            conn.disconnect()
            ok
        } catch (e: Exception) {
            false
        }
    }

    /** The feed of the last [hours] hours, or null when the server can't be reached. */
    fun getFeed(hours: Int): Feed? {
        return try {
            val conn = open("/api/feed?hours=$hours", "GET")
            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            Feed.parse(JSONObject(text))
        } catch (e: Exception) {
            null
        }
    }

    /** Raw JPEG bytes of one screenshot, or null. */
    fun getImage(time: Long): ByteArray? {
        return try {
            val conn = open("/api/img?t=$time", "GET")
            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            bytes
        } catch (e: Exception) {
            null
        }
    }
}

data class Feed(
    val now: Long,
    val lastSeenTime: Long,
    val lastSeenState: String,
    val lastEventType: String,
    val lastEventTime: Long,
    val shots: List<Long>,
) {
    companion object {
        fun parse(json: JSONObject): Feed {
            val seen = json.optJSONObject("lastseen")
            val events = json.optJSONArray("events")
            val lastEvent = events?.optJSONObject(0)
            val arr = json.optJSONArray("shots")
            val shots = ArrayList<Long>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    shots.add(arr.getLong(i))
                }
            }
            return Feed(
                now = json.optLong("now", System.currentTimeMillis()),
                lastSeenTime = seen?.optLong("time", 0) ?: 0,
                lastSeenState = seen?.optString("state", "") ?: "",
                lastEventType = lastEvent?.optString("type", "") ?: "",
                lastEventTime = lastEvent?.optLong("time", 0) ?: 0,
                shots = shots,
            )
        }
    }
}
