package com.clinic.tickets

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
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
        if (!License.isActivated(this)) {
            startActivity(Intent(this, ActivationActivity::class.java))
            finish()
            return
        }
        db = Db(this)
        printer = PrinterHelper(this)
        printer.connect()

        val root = column()
        title = header("")
        status = label("", 15f, false, true)
        root.addView(title, wrapParams())
        root.addView(status, wrapParams())

        val h = dp(90)
        val r1 = row()
        r1.addView(button(Types.EXAM, 24f, C.PRIMARY) { issue(Types.EXAM) }, inRow(1f, h))
        r1.addView(button(Types.URGENT, 24f, C.URGENT) { issue(Types.URGENT) }, inRow(1f, h))
        root.addView(r1, wrapParams())
        val r2 = row()
        r2.addView(button(Types.REVIEW, 24f, C.GREEN) { issue(Types.REVIEW) }, inRow(1f, h))
        r2.addView(button(Types.CONSULT, 24f, C.TEAL) { issue(Types.CONSULT) }, inRow(1f, h))
        root.addView(r2, wrapParams())

        root.addView(View(this), inColumn(1f))

        val tools = row()
        tools.addView(button("إعادة طباعة آخر تذكرة", 14f, C.GRAY) { reprint() }, inRow())
        tools.addView(button("إلغاء تذكرة", 14f, C.GRAY) { cancelDialog() }, inRow())
        root.addView(tools, wrapParams())

        val nav1 = row()
        nav1.addView(button("الاستدعاء", 18f) { startActivity(Intent(this, CallActivity::class.java)) }, inRow())
        nav1.addView(button("السجل", 18f) {
            requirePin { startActivity(Intent(this, LogActivity::class.java)) }
        }, inRow())
        root.addView(nav1, wrapParams())

        val nav2 = row()
        nav2.addView(button("الإعدادات", 18f, C.GRAY) {
            requirePin { startActivity(Intent(this, SettingsActivity::class.java)) }
        }, inRow())
        nav2.addView(button("الدعم الفني", 18f, C.GRAY) {
            startActivity(Intent(this, SupportActivity::class.java))
        }, inRow())
        root.addView(nav2, wrapParams())

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (!::db.isInitialized) return
        refresh()
        // الاتصال بالطابعة بيحتاج لحظات بعد فتح البرنامج
        status.postDelayed({ refresh() }, 1500)
    }

    override fun onDestroy() {
        if (::printer.isInitialized) printer.disconnect()
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
        printNow(ticket, ahead)
    }

    private fun printNow(ticket: Ticket, ahead: Int) {
        val extras = listOf(1, 2, 3).map { Settings.line(this, it) }.filter { it.isNotBlank() }
        printer.printTicket(
            Settings.clinic(this), Settings.doctor(this), Settings.footer(this), extras, ticket, ahead
        ) { ok ->
            if (!ok) {
                Toast.makeText(
                    this,
                    "تعذرت الطباعة للتذكرة رقم ${ticket.number}: ${printer.lastError}",
                    Toast.LENGTH_LONG
                ).show()
            }
            refresh()
        }
    }

    /** إعادة طباعة آخر تذكرة صدرت اليوم بنفس رقمها */
    private fun reprint() {
        if (!printer.isReady()) {
            Toast.makeText(this, "الطابعة غير متصلة", Toast.LENGTH_LONG).show()
            return
        }
        val t = db.lastIssued()
        if (t == null) {
            Toast.makeText(this, "لا توجد تذاكر اليوم", Toast.LENGTH_SHORT).show()
            return
        }
        val ahead = if (t.status == "waiting") db.aheadOf(t) else -1
        printNow(t, ahead)
    }

    private fun cancelDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "رقم التذكرة"
            gravity = Gravity.CENTER
            textSize = 22f
        }
        AlertDialog.Builder(this)
            .setTitle("إلغاء تذكرة")
            .setMessage("اكتب رقم التذكرة، أو اتركه فارغاً لإلغاء آخر تذكرة.")
            .setView(input)
            .setPositiveButton("إلغاء التذكرة") { _, _ ->
                val txt = input.text.toString().trim()
                val number = if (txt.isEmpty()) null else txt.toIntOrNull()
                if (txt.isNotEmpty() && number == null) {
                    Toast.makeText(this, "رقم غير صحيح", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, db.cancel(number), Toast.LENGTH_LONG).show()
                    refresh()
                }
            }
            .setNegativeButton("رجوع", null)
            .show()
    }
}
