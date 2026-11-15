package com.nave.screenreview

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** The reviewer's screen: status, the latest screenshot, and all screenshots of the last day. */
class ViewerActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var latest: ImageView
    private lateinit var latestLabel: TextView
    private lateinit var grid: GridView
    private var shots: List<Long> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = Ui.column(this)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        status = Ui.body(this, "טוען...").apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val refresh = TextView(this).apply {
            text = "רענן"
            textSize = 16f
            setTextColor(getColor(R.color.accent))
            setPadding(Ui.dp(context, 12), 0, 0, Ui.dp(context, 12))
            setOnClickListener { load() }
        }
        top.addView(status)
        top.addView(refresh)
        col.addView(top)

        latest = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.parseColor("#F0F2F5"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.3f)
            setOnClickListener { (tag as? Long)?.let { openFull(it) } }
        }
        col.addView(latest)
        latestLabel = Ui.body(this, "", muted = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 6))
        }
        col.addView(latestLabel)

        grid = GridView(this).apply {
            numColumns = 4
            horizontalSpacing = Ui.dp(context, 6)
            verticalSpacing = Ui.dp(context, 6)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            adapter = ShotsAdapter()
            setOnItemClickListener { _, _, pos, _ -> openFull(shots[pos]) }
        }
        col.addView(grid)
        setContentView(col)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        status.text = "טוען..."
        io.execute {
            val feed = Api.getFeed(24)
            main.post { show(feed) }
            Images.cleanup(this)
            ShotWidget.updateAll(this)
        }
    }

    private fun show(feed: Feed?) {
        if (feed == null) {
            status.text = "אין חיבור לשרת. נסה שוב."
            status.setTextColor(getColor(R.color.warn))
            return
        }
        val stale = feed.now - feed.lastSeenTime > 30 * 60_000L
        val stopped = feed.lastSeenState == "stop" || feed.lastSeenState == "reboot"
        val lines = StringBuilder()
        lines.append("קשר אחרון: ")
            .append(if (feed.lastSeenTime > 0) Fmt.ago(feed.lastSeenTime, feed.now) else "אין עדיין")
        lines.append("\nמצב: ").append(Fmt.state(feed.lastSeenState))
        if (feed.lastEventType == "stop" || feed.lastEventType == "reboot") {
            lines.append("\nאירוע אחרון: ").append(Fmt.state(feed.lastEventType))
                .append(" ב-").append(Fmt.dayClock(feed.lastEventTime))
        }
        lines.append("\nצילומים ב-24 השעות האחרונות: ").append(feed.shots.size)
        status.text = lines
        status.setTextColor(getColor(if (stopped || stale) R.color.warn else R.color.ink))

        shots = feed.shots
        (grid.adapter as BaseAdapter).notifyDataSetChanged()
        val newest = shots.firstOrNull()
        if (newest != null) {
            latest.tag = newest
            Images.show(latest, newest, 1280)
            latestLabel.text = "הצילום האחרון: " + Fmt.dayClock(newest) + " (" + Fmt.ago(newest, feed.now) + ")"
        } else {
            latest.setImageDrawable(null)
            latestLabel.text = "אין צילומים ב-24 השעות האחרונות"
        }
    }

    private fun openFull(time: Long) {
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val frame = FrameLayout(this)
        val img = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val label = TextView(this).apply {
            text = Fmt.dayClock(time) + "   (לחץ לסגירה)"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#99000000"))
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 8))
        }
        frame.addView(img, FrameLayout.LayoutParams(-1, -1))
        frame.addView(label, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        frame.setOnClickListener { d.dismiss() }
        d.setContentView(frame)
        Images.show(img, time, 1600)
        d.show()
    }

    private inner class ShotsAdapter : BaseAdapter() {
        override fun getCount() = shots.size
        override fun getItem(position: Int) = shots[position]
        override fun getItemId(position: Int) = shots[position]

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val cell = (convertView as? LinearLayout) ?: LinearLayout(this@ViewerActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(Color.parseColor("#F0F2F5"))
                    layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(context, 120))
                })
                addView(TextView(context).apply {
                    gravity = Gravity.CENTER
                    textSize = 12f
                    setTextColor(getColor(R.color.muted))
                })
            }
            val t = shots[position]
            Images.show(cell.getChildAt(0) as ImageView, t, 320)
            (cell.getChildAt(1) as TextView).text = Fmt.clock(t)
            return cell
        }
    }
}
