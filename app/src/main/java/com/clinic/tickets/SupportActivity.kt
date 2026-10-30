package com.clinic.tickets

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Toast

class SupportActivity : Activity() {

    private fun field(hintText: String, value: String) = EditText(this).apply {
        hint = hintText
        setText(value)
        textSize = 18f
        gravity = Gravity.START
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    private fun version(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "لا يوجد تطبيق مناسب على هذا الجهاز", Toast.LENGTH_LONG).show()
        }
    }

    private fun build() {
        val root = column()
        root.addView(header("الدعم الفني"), wrapParams())

        val name = Settings.support(this, "name")
        val phone = Settings.support(this, "phone")
        val wa = Settings.support(this, "whatsapp")
        val mail = Settings.support(this, "email")
        val note = Settings.support(this, "note")

        if (listOf(name, phone, wa, mail, note).all { it.isBlank() }) {
            root.addView(label("لم تُضبط بيانات الدعم الفني بعد.", 16f, false, true), wrapParams())
        } else {
            if (name.isNotBlank()) root.addView(label(name, 22f, true, true), wrapParams())
            if (note.isNotBlank()) root.addView(label(note, 16f, false, true), wrapParams())
            if (phone.isNotBlank()) {
                root.addView(button("اتصال: $phone", 18f) {
                    open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)))
                }, wrapParams())
            }
            if (wa.isNotBlank()) {
                root.addView(button("واتساب: $wa", 18f, C.GREEN) {
                    open(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + wa.filter { it.isDigit() })))
                }, wrapParams())
            }
            if (mail.isNotBlank()) {
                root.addView(button("بريد: $mail", 18f, C.GRAY) {
                    open(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + mail)))
                }, wrapParams())
            }
        }

        root.addView(label("──────────", 14f, false, true), wrapParams())
        root.addView(label("إصدار البرنامج: " + version(), 14f), wrapParams())
        val codeView = label("كود الجهاز: " + License.deviceCode(this), 14f)
        codeView.setTextIsSelectable(true)
        root.addView(codeView, wrapParams())

        if (License.isSupportUnlocked(this)) {
            root.addView(label("تعديل بيانات الدعم (مفتوح)", 16f, true), wrapParams())
            val fName = field("الاسم أو الجهة", name)
            val fPhone = field("رقم الهاتف", phone)
            val fWa = field("رقم واتساب (بالصيغة الدولية)", wa)
            val fMail = field("البريد الإلكتروني", mail)
            val fNote = field("ملاحظة (ساعات العمل مثلاً)", note)
            for (f in listOf(fName, fPhone, fWa, fMail, fNote)) root.addView(f, wrapParams())
            root.addView(button("حفظ بيانات الدعم", 18f, C.GREEN) {
                Settings.saveSupport(
                    this, fName.text.toString().trim(), fPhone.text.toString().trim(),
                    fWa.text.toString().trim(), fMail.text.toString().trim(), fNote.text.toString().trim()
                )
                Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show()
                build()
            }, wrapParams())
            root.addView(button("قفل التعديل", 16f, C.GRAY) {
                License.lockSupport(this)
                build()
            }, wrapParams())
        } else {
            root.addView(button("تعديل بيانات الدعم (للمطوّر)", 14f, C.GRAY) { askDevCode() }, wrapParams())
        }

        root.addView(button("رجوع", 18f, C.GRAY) { finish() }, wrapParams())

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(C.BG)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun askDevCode() {
        val input = EditText(this).apply {
            hint = "كود المطوّر"
            textSize = 14f
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        AlertDialog.Builder(this)
            .setTitle("تعديل بيانات الدعم")
            .setMessage("أدخل كود المطوّر لهذا الجهاز.\nكود الجهاز: " + License.deviceCode(this))
            .setView(input)
            .setPositiveButton("فتح") { _, _ ->
                if (License.unlockSupport(this, input.text.toString())) {
                    build()
                } else {
                    Toast.makeText(this, "الكود غير صحيح لهذا الجهاز", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
