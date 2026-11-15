package com.nave.screenreview

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Tiny helpers so the screens can be built in code with one consistent look. */
object Ui {
    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun column(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(ctx.getColor(R.color.bg))
        val p = dp(ctx, 20)
        setPadding(p, p, p, p)
    }

    fun title(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 22f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(ctx.getColor(R.color.ink))
        setPadding(0, 0, 0, dp(ctx, 12))
    }

    fun body(ctx: Context, text: String, muted: Boolean = false): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 15f
        setLineSpacing(0f, 1.25f)
        setTextColor(ctx.getColor(if (muted) R.color.muted else R.color.ink))
        setPadding(0, 0, 0, dp(ctx, 12))
    }

    fun button(ctx: Context, text: String, color: Int = R.color.accent, onClick: () -> Unit): Button =
        Button(ctx).apply {
            this.text = text
            textSize = 17f
            isAllCaps = false
            setTextColor(ctx.getColor(R.color.bg))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 12).toFloat()
                setColor(ctx.getColor(color))
            }
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 56)
            ).apply { topMargin = dp(ctx, 12) }
        }
}
