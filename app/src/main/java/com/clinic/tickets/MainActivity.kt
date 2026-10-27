package com.clinic.tickets

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var db: Db
    private lateinit var printer: PrinterHelper
    private lateinit var title: TextView
    private lateinit var status: TextView
    private var lastClick = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Db(this)
        printer = PrinterHelper(this)
        printer.connect()

        val root = column()
        title = label("", 28f, true, true)
        status = label("", 16f, false, true)
        root.addView(title, wrapParams())
        root.addView(status, wrapParams())

        val big = row()
        big.addView(button(Types.EXAM, 34f) { issue(Types.EXAM) }, inRow(1f, LinearLayout.LayoutParams.MATCH_PARENT))
        big.addView(button(Types.REVIEW, 34f) { issue(Types.REVIEW) }, inRow(1f, LinearLayout.LayoutParams.MATCH_PARENT))
        root.addView(big, inColumn(1f))

        val bottom = row()
        bottom.addView(button("الاستدعاء", 18f) { startActivity(Intent(this, CallActivity::class.java)) }, inRow())
        bottom.addView(button("السجل", 18f) { startActivity(Intent(this, LogActivity::class.java)) }, inRow())
        bottom.addView(button("الإعدادات", 18f) { startActivity(Intent(this, SettingsActivity::class.java)) }, inRow())
        root.addView(bottom, wrapParams())

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        // الاتصال بالطابعة بيحتاج لحظات بعد فتح البرنامج
        status.postDelayed({ refresh() }, 1500)
    }

    override fun onDestroy() {
        printer.disconnect()
        super.onDestroy()
    }

    private fun refresh() {
        title.text = Settings.clinic(this)
        val p = if (printer.isReady()) "متصلة" else "غير متصلة"
        status.text = "المنتظرون: ${db.waitingTotal()}   |   آخر رقم: ${db.lastNumber()}   |   الطابعة: $p"
    }

    private fun issue(type: String) {
        val now = System.currentTimeMillis()
        if (now - lastClick < 1500) return // منع الضغط المزدوج
        lastClick = now
        if (!printer.isReady()) {
            Toast.makeText(this, "الطابعة غير متصلة", Toast.LENGTH_LONG).show()
            return
        }
        val (ticket, ahead) = db.issue(type)
        val ok = printer.printTicket(
            Settings.clinic(this), Settings.doctor(this), Settings.footer(this), ticket, ahead
        )
        if (!ok) {
            Toast.makeText(this, "تعذرت الطباعة للتذكرة رقم ${ticket.number}", Toast.LENGTH_LONG).show()
        }
        refresh()
    }
}
