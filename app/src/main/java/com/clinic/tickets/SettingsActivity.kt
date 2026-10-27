package com.clinic.tickets

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var printer: PrinterHelper
    private lateinit var clinic: EditText
    private lateinit var doctor: EditText
    private lateinit var footer: EditText

    private fun field(hintText: String, value: String) = EditText(this).apply {
        hint = hintText
        setText(value)
        textSize = 20f
        gravity = Gravity.START
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        printer = PrinterHelper(this)
        printer.connect()

        val root = column()
        root.addView(label("الإعدادات", 26f, true, true), wrapParams())

        clinic = field("اسم العيادة", Settings.clinic(this))
        doctor = field("اسم الدكتور (اختياري)", Settings.doctor(this))
        footer = field("نص أسفل التذكرة", Settings.footer(this))
        root.addView(clinic, wrapParams())
        root.addView(doctor, wrapParams())
        root.addView(footer, wrapParams())

        root.addView(button("حفظ", 20f) { save() }, wrapParams())
        root.addView(button("طباعة تذكرة تجريبية", 18f) { testPrint() }, wrapParams())
        root.addView(button("رجوع", 18f) { finish() }, wrapParams())

        setContentView(root)
    }

    override fun onDestroy() {
        printer.disconnect()
        super.onDestroy()
    }

    private fun save() {
        Settings.save(this, clinic.text.toString().trim(), doctor.text.toString().trim(), footer.text.toString().trim())
        Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show()
    }

    private fun testPrint() {
        save()
        if (!printer.isReady()) {
            Toast.makeText(this, "الطابعة غير متصلة", Toast.LENGTH_LONG).show()
            return
        }
        val t = Ticket(0, Db.today(), 1, Types.EXAM, System.currentTimeMillis(), null, "waiting")
        val ok = printer.printTicket(Settings.clinic(this), Settings.doctor(this), Settings.footer(this), t, 0)
        if (!ok) Toast.makeText(this, "تعذرت الطباعة", Toast.LENGTH_LONG).show()
    }
}
