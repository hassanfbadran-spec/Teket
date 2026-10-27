package com.clinic.tickets

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.column(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(16), dp(16), dp(16), dp(16))
    layoutDirection = View.LAYOUT_DIRECTION_RTL
}

fun Context.row(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    layoutDirection = View.LAYOUT_DIRECTION_RTL
}

fun Context.label(text: String, size: Float = 20f, bold: Boolean = false, center: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size
        if (bold) setTypeface(typeface, Typeface.BOLD)
        if (center) gravity = Gravity.CENTER
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

fun Context.button(text: String, size: Float = 20f, onClick: () -> Unit): Button =
    Button(this).apply {
        this.text = text
        textSize = size
        isAllCaps = false
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
