package com.clinic.tickets

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var printer: PrinterHelper
    private lateinit var clinic: EditText
    private lateinit var doctor: EditText
    private lateinit var footer: EditText
    private lateinit var line1: EditText
    private lateinit var line2: EditText
    private lateinit var line3: EditText
    private lateinit var printerLabel: TextView
    private lateinit var paperBtn: Button

    private fun field(hintText: String, value: String) = EditText(this).apply {
        hint = hintText
        setText(value)
        textSize = 18f
        gravity = Gravity.START
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        printer = PrinterHelper(this)
        printer.connect()

        val root = column()
        root.addView(header("الإعدادات"), wrapParams())

        clinic = field("اسم العيادة", Settings.clinic(this))
        doctor = field("اسم الدكتور (اختياري)", Settings.doctor(this))
        footer = field("نص أسفل التذكرة", Settings.footer(this))
        root.addView(clinic, wrapParams())
        root.addView(doctor, wrapParams())
        root.addView(footer, wrapParams())

        root.addView(label("أسطر إضافية في آخر التذكرة (اختيارية)", 16f, true), wrapParams())
        line1 = field("سطر إضافي 1", Settings.line(this, 1))
        line2 = field("سطر إضافي 2", Settings.line(this, 2))
        line3 = field("سطر إضافي 3", Settings.line(this, 3))
        root.addView(line1, wrapParams())
        root.addView(line2, wrapParams())
        root.addView(line3, wrapParams())

        root.addView(label("الطابعة", 16f, true), wrapParams())
        printerLabel = label("", 16f)
        root.addView(printerLabel, wrapParams())
        root.addView(button("اختيار الطابعة", 18f) { pickPrinter() }, wrapParams())
        paperBtn = button("", 16f, C.GRAY) { togglePaper() }
        root.addView(paperBtn, wrapParams())
        root.addView(
            label("لطابعة بلوتوث: اقرنها أولاً من إعدادات البلوتوث في الجهاز (غالباً الرمز 0000 أو 1234)، ثم اخترها من هنا.", 13f),
            wrapParams()
        )

        root.addView(button("حفظ", 20f, C.GREEN) { save(true) }, wrapParams())
        root.addView(button("طباعة تذكرة تجريبية", 18f) { testPrint() }, wrapParams())
        root.addView(button("رجوع", 18f, C.GRAY) { finish() }, wrapParams())

        refreshPrinterInfo()
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(C.BG)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onDestroy() {
        printer.disconnect()
        super.onDestroy()
    }

    private fun refreshPrinterInfo() {
        val addr = Settings.btAddress(this)
        printerLabel.text = if (addr.isEmpty()) "الحالية: المدمجة (Sunmi)" else "الحالية: ${Settings.btName(this)} (بلوتوث)"
        paperBtn.text = "عرض الورق (للبلوتوث): " + if (Settings.paperDots(this) == 576) "80mm" else "58mm"
    }

    private fun togglePaper() {
        Settings.savePaper(this, if (Settings.paperDots(this) == 576) 384 else 576)
        refreshPrinterInfo()
    }

    private fun pickPrinter() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 7)
            return
        }
        showDevices()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showDevices()
        } else if (requestCode == 7) {
            Toast.makeText(this, "إذن البلوتوث مطلوب لاختيار الطابعة", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "فعّل البلوتوث أولاً", Toast.LENGTH_LONG).show()
            return
        }
        val devices = adapter.bondedDevices.toList()
        val names = mutableListOf("الطابعة المدمجة (Sunmi)")
        devices.forEach { names.add("${it.name ?: "?"}  (${it.address})") }
        AlertDialog.Builder(this)
            .setTitle("اختر الطابعة")
            .setItems(names.toTypedArray()) { _, i ->
                if (i == 0) {
                    Settings.saveBt(this, "", "")
                } else {
                    val d = devices[i - 1]
                    Settings.saveBt(this, d.address, d.name ?: d.address)
                }
                refreshPrinterInfo()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun save(toast: Boolean) {
        Settings.save(
            this,
            clinic.text.toString().trim(), doctor.text.toString().trim(), footer.text.toString().trim(),
            line1.text.toString().trim(), line2.text.toString().trim(), line3.text.toString().trim()
        )
        if (toast) Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show()
    }

    private fun testPrint() {
        save(false)
        if (!printer.isReady()) {
            Toast.makeText(this, "الطابعة غير متصلة", Toast.LENGTH_LONG).show()
            return
        }
        val t = Ticket(0, Db.today(), 1, Types.EXAM, System.currentTimeMillis(), null, "waiting")
        val extras = listOf(1, 2, 3).map { Settings.line(this, it) }.filter { it.isNotBlank() }
        printer.printTicket(
            Settings.clinic(this), Settings.doctor(this), Settings.footer(this), extras, t, 0
        ) { ok ->
            if (!ok) Toast.makeText(this, "تعذرت الطباعة: ${printer.lastError}", Toast.LENGTH_LONG).show()
        }
    }
}
