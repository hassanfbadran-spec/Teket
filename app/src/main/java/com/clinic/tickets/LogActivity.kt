package com.clinic.tickets

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class LogActivity : Activity() {

    private lateinit var db: Db
    private lateinit var printer: PrinterHelper
    private lateinit var period: TextView
    private lateinit var body: TextView
    private var mode = 0 // 0 يومي، 1 شهري، 2 سنوي
    private val cal = Calendar.getInstance()
    private var lines: List<String> = emptyList()
    private var title = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Db(this)
        printer = PrinterHelper(this)
        printer.connect()

        val root = column()

        val modes = row()
        modes.addView(button("يومي", 18f) { mode = 0; render() }, inRow())
        modes.addView(button("شهري", 18f) { mode = 1; render() }, inRow())
        modes.addView(button("سنوي", 18f) { mode = 2; render() }, inRow())
        root.addView(modes, wrapParams())

        val nav = row()
        nav.addView(button("السابق", 16f) { shift(-1) }, inRow())
        period = label("", 20f, true, true)
        nav.addView(period, inRow(2f))
        nav.addView(button("التالي", 16f) { shift(1) }, inRow())
        root.addView(nav, wrapParams())

        body = label("", 18f)
        val scroll = ScrollView(this)
        scroll.addView(body)
        root.addView(scroll, inColumn(1f))

        val bottom = row()
        bottom.addView(button("طباعة ملخص", 18f) { printSummary() }, inRow())
        bottom.addView(button("رجوع", 18f) { finish() }, inRow())
        root.addView(bottom, wrapParams())

        setContentView(root)
        render()
    }

    override fun onDestroy() {
        printer.disconnect()
        super.onDestroy()
    }

    private fun fmt() = SimpleDateFormat(
        when (mode) {
            0 -> "yyyy-MM-dd"
            1 -> "yyyy-MM"
            else -> "yyyy"
        }, Locale.US
    )

    private fun shift(d: Int) {
        cal.add(
            when (mode) {
                0 -> Calendar.DAY_OF_MONTH
                1 -> Calendar.MONTH
                else -> Calendar.YEAR
            }, d
        )
        render()
    }

    private fun hm(ms: Long) = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

    private fun ticketLine(t: Ticket): String {
        val called = t.calledAt?.let { " ← ${hm(it)}" } ?: ""
        return "#${t.number} ${t.type}  ${hm(t.issuedAt)}$called  (${statusAr(t.status)})"
    }

    private fun render() {
        val prefix = fmt().format(cal.time)
        val modeName = listOf("يومي", "شهري", "سنوي")[mode]
        period.text = "$modeName: $prefix"
        title = "سجل $modeName $prefix"

        val out = mutableListOf<String>()
        val total = db.stats(prefix, 0).firstOrNull()
        if (total == null) {
            out.add("لا توجد تذاكر في هذه الفترة")
        } else {
            out.add("الإجمالي: ${total.total}")
            out.add("كشف: ${total.exam}   |   مراجعة: ${total.review}")
            out.add("تخطي: ${total.skipped}")
            out.add("متوسط الانتظار: ${total.avgWaitMin} دقيقة")
            out.add("──────────")
            if (mode == 0) {
                db.ticketsOfDay(prefix).forEach { out.add(ticketLine(it)) }
            } else {
                db.stats(prefix, if (mode == 1) 10 else 7).forEach {
                    out.add("${it.label}:  ${it.total}  (كشف ${it.exam} / مراجعة ${it.review})")
                }
            }
        }
        lines = out
        body.text = out.joinToString("\n")
    }

    private fun printSummary() {
        if (!printer.isReady()) {
            Toast.makeText(this, "الطابعة غير متصلة", Toast.LENGTH_LONG).show()
            return
        }
        val ok = printer.printSummary(Settings.clinic(this), title, lines)
        if (!ok) Toast.makeText(this, "تعذرت الطباعة", Toast.LENGTH_LONG).show()
    }
}
