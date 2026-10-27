package com.clinic.tickets

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
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
        title = header("")
        status = label("", 15f, false, true)
        root.addView(title, wrapParams())
        root.addView(status, wrapParams())

        val big = row()
        val h = dp(100)
        big.addView(button(Types.EXAM, 30f, C.PRIMARY) { issue(Types.EXAM) }, inRow(1f, h))
        big.addView(button(Types.REVIEW, 30f, C.GREEN) { issue(Types.REVIEW) }, inRow(1f, h))
        root.addView(big, wrapParams())

        root.addView(View(this), inColumn(1f))

        val bottom = row()
        bottom.addView(button("الاستدعاء", 18f) { startActivity(Intent(this, CallActivity::class.java)) }, inRow())
        bottom.addView(button("السجل", 18f) { startActivity(Intent(this, LogActivity::class.java)) }, inRow())
        bottom.addView(button("الإعدادات", 18f, C.GRAY) { startActivity(Intent(this, SettingsActivity::class.java)) }, inRow())
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
        status.text = "المنتظرون: ${db.waitingTotal()}   |   آخر رقم: ${db.lastNumber()}   |   الطابعة: ${printer.statusText()}"
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
        val extras = listOf(1, 2, 3).map { Settings.line(this, it) }.filter { it.isNotBlank() }
        printer.printTicket(
            Settings.clinic(this), Settings.doctor(this), Settings.footer(this), extras, ticket, ahead
        ) { ok ->
            if (!ok) {
                Toast.makeText(this, "تعذرت الطباعة للتذكرة رقم ${ticket.number}", Toast.LENGTH_LONG).show()
            }
            refresh()
        }
    }
}
