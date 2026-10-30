package com.clinic.tickets

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : Activity() {

    private lateinit var db: Db
    private lateinit var printer: PrinterHelper
    private lateinit var clinic: EditText
    private lateinit var doctor: EditText
    private lateinit var footer: EditText
    private lateinit var line1: EditText
    private lateinit var line2: EditText
    private lateinit var line3: EditText
    private lateinit var pinField: EditText
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
        db = Db(this)
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

        root.addView(label("كلمة سر الإعدادات", 16f, true), wrapParams())
        root.addView(label("الافتراضية admin. يمكنك تغييرها هنا (4 أحرف على الأقل).", 13f), wrapParams())
        pinField = field("كلمة سر جديدة", "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(pinField, wrapParams())
        root.addView(button("حفظ كلمة السر", 16f) { savePin() }, wrapParams())
        root.addView(button("إرجاع كلمة السر إلى admin", 16f, C.GRAY) {
            Settings.clearPin(this)
            Toast.makeText(this, "تمت إعادة كلمة السر إلى admin", Toast.LENGTH_SHORT).show()
        }, wrapParams())

        root.addView(label("النسخ الاحتياطي", 16f, true), wrapParams())
        root.addView(label("يحفظ سجل التذاكر والإعدادات في ملف تختار مكانه (لا يشمل التفعيل ولا الطابعة).", 13f), wrapParams())
        root.addView(button("تصدير نسخة احتياطية", 16f) { exportBackup() }, wrapParams())
        root.addView(button("استرجاع نسخة احتياطية", 16f, C.GRAY) { importBackup() }, wrapParams())

        root.addView(button("تواصل معنا / الدعم الفني", 18f, C.GREEN) {
            startActivity(Intent(this, SupportActivity::class.java))
        }, wrapParams())
        root.addView(button("رجوع", 18f, C.GRAY) { finish() }, wrapParams())

        refreshPrinterInfo()
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(C.BG)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onDestroy() {
        if (::printer.isInitialized) printer.disconnect()
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

    private fun savePin() {
        val pin = pinField.text.toString().trim()
        if (pin.length < 4) {
            Toast.makeText(this, "كلمة السر يجب ألا تقل عن 4 أحرف", Toast.LENGTH_LONG).show()
            return
        }
        Settings.setPin(this, pin)
        pinField.setText("")
        Toast.makeText(this, "تم حفظ كلمة السر", Toast.LENGTH_SHORT).show()
    }

    // ---------- النسخ الاحتياطي ----------
    private fun exportBackup() {
        val name = "clinic-tickets-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/json"
        i.putExtra(Intent.EXTRA_TITLE, name)
        startActivityForResult(i, 101)
    }

    private fun importBackup() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        startActivityForResult(i, 102)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            101 -> writeBackup(uri)
            102 -> AlertDialog.Builder(this)
                .setTitle("استرجاع نسخة احتياطية")
                .setMessage("سيتم استبدال سجل التذاكر الحالي والإعدادات بمحتوى النسخة. هل تريد المتابعة؟")
                .setPositiveButton("استرجاع") { _, _ -> readBackup(uri) }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    private fun writeBackup(uri: Uri) {
        try {
            val text = Backup.build(this, db)
            contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(this, "تم تصدير النسخة الاحتياطية", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر التصدير: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun readBackup(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
                ?: throw IllegalStateException("تعذرت قراءة الملف")
            val n = Backup.restore(this, db, text)
            Toast.makeText(this, "تم استرجاع $n تذكرة", Toast.LENGTH_LONG).show()
            recreate()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر الاسترجاع: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
