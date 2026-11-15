package com.nave.screenreview

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** First run: choose what this phone is. After that it opens the right screen directly. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (Prefs.mode(this)) {
            Prefs.MODE_CAPTURE -> return open(CaptureActivity::class.java)
            Prefs.MODE_VIEWER -> return open(ViewerActivity::class.java)
        }
        val col = Ui.column(this)
        col.addView(Ui.title(this, "צילום מסך לביקורת"))
        col.addView(Ui.body(this, "מה הטלפון הזה?\nהבחירה נשמרת. כדי לשנות אותה, מוחקים את נתוני האפליקציה."))
        col.addView(Ui.button(this, "הטלפון שלי, שאותו מצלמים") {
            Prefs.setMode(this, Prefs.MODE_CAPTURE)
            open(CaptureActivity::class.java)
        })
        col.addView(Ui.button(this, "טלפון הצופה (מקבל את הצילומים)", R.color.ink) {
            Prefs.setMode(this, Prefs.MODE_VIEWER)
            open(ViewerActivity::class.java)
        })
        setContentView(col)
    }

    private fun open(cls: Class<*>) {
        startActivity(Intent(this, cls))
        finish()
    }
}
