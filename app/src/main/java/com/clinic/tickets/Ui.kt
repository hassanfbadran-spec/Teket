package com.clinic.tickets

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object C {
    val PRIMARY = 0xFF1565C0.toInt()
    val DARK = 0xFF0D47A1.toInt()
    val GREEN = 0xFF2E7D32.toInt()
    val BG = 0xFFF4F8FB.toInt()
    val MUTED = 0xFF90A4AE.toInt()
    val GRAY = 0xFF607D8B.toInt()
    val INK = 0xFF1B2A38.toInt()
    val URGENT = 0xFFD84315.toInt()
    val TEAL = 0xFF00838F.toInt()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.column(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(16), dp(16), dp(16), dp(16))
    layoutDirection = View.LAYOUT_DIRECTION_RTL
    setBackgroundColor(C.BG)
}

fun Context.row(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    layoutDirection = View.LAYOUT_DIRECTION_RTL
}

fun Context.label(text: String, size: Float = 20f, bold: Boolean = false, center: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(C.INK)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        if (center) gravity = Gravity.CENTER
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

/** شريط عنوان أزرق بحواف مدورة */
fun Context.header(text: String): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 24f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(12), dp(8), dp(12))
        background = GradientDrawable().apply {
            setColor(C.PRIMARY)
            cornerRadius = dp(12).toFloat()
        }
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

fun Button.fillColor(color: Int) {
    val d = resources.displayMetrics.density
    background = GradientDrawable().apply {
        setColor(color)
        cornerRadius = 12f * d
    }
}

fun Context.button(text: String, size: Float = 20f, color: Int = C.PRIMARY, onClick: () -> Unit): Button =
    Button(this).apply {
        this.text = text
        textSize = size
        isAllCaps = false
        setTextColor(Color.WHITE)
        fillColor(color)
        setOnClickListener { onClick() }
    }

/** عنصر داخل صف أفقي بعرض نسبي */
fun Context.inRow(w: Float = 1f, height: Int = LinearLayout.LayoutParams.WRAP_CONTENT) =
    LinearLayout.LayoutParams(0, height, w).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }

/** عنصر داخل عمود رأسي بارتفاع نسبي */
fun Context.inColumn(w: Float = 1f) =
    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, w).apply {
        setMargins(0, dp(4), 0, dp(4))
    }

fun Context.wrapParams() =
    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { setMargins(0, dp(4), 0, dp(4)) }
