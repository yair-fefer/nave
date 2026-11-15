package com.nave.screenreview

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView

/** The screen on the phone being captured: explains everything and has one start/stop button. */
class CaptureActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var col: android.widget.LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        col = Ui.column(this)
        col.addView(Ui.title(this, "צילום מסך לביקורת"))
        col.addView(
            Ui.body(
                this,
                "מה האפליקציה עושה, בשקיפות מלאה:\n" +
                    "• כשהמסך דלוק ופתוח, היא מצלמת את המסך בזמן אקראי, פעם ב-3 עד 9 דקות.\n" +
                    "• כל צילום נשלח לטלפון הצופה. הוא נשמר 48 שעות ונמחק.\n" +
                    "• כשהמסך כבוי או נעול, לא מצלמים.\n" +
                    "• כל עוד הצילום פעיל, יש התראה קבועה בשורת ההתראות.\n" +
                    "• אפשר לעצור בכל רגע. הצופה רואה שהצילום הופסק."
            )
        )
        status = Ui.body(this, "")
        col.addView(status)
        if (CaptureService.running) {
            status.text = "מצב: פעיל"
            status.setTextColor(getColor(R.color.ok))
            col.addView(Ui.button(this, "עצור צילום", R.color.warn) {
                startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
                status.text = "עוצר..."
                col.postDelayed({ render() }, 800)
            })
        } else {
            status.text = "מצב: כבוי"
            status.setTextColor(getColor(R.color.muted))
            col.addView(Ui.body(this, "בחלון הבא של אנדרואיד, אם תופיע בחירה, בחר \"כל המסך\" ואז \"התחל\".", muted = true))
            col.addView(Ui.button(this, "הפעל צילום") { askConsent() })
        }
        setContentView(col)
    }

    private fun askConsent() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= 34) {
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mpm.createScreenCaptureIntent()
        }
        startActivityForResult(intent, REQ_CAPTURE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAPTURE) {
            return
        }
        if (resultCode != RESULT_OK || data == null) {
            status.text = "לא אושר. הצילום לא הופעל."
            return
        }
        val svc = Intent(this, CaptureService::class.java)
            .putExtra(CaptureService.EXTRA_CODE, resultCode)
            .putExtra(CaptureService.EXTRA_DATA, data)
        startForegroundService(svc)
        col.postDelayed({ render() }, 800)
    }

    companion object {
        private const val REQ_CAPTURE = 7
    }
}
