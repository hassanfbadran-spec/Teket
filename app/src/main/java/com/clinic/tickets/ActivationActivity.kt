package com.clinic.tickets

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Toast

class ActivationActivity : Activity() {

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (License.isActivated(this)) {
            goMain()
            return
        }
        val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val code = License.deviceCode(this)

        val root = column()
        root.addView(header("تفعيل البرنامج"), wrapParams())
        root.addView(label("كود الجهاز", 16f, true), wrapParams())
        val codeView = label(code, 26f, true, true).apply {
            setTextColor(C.DARK)
            setTextIsSelectable(true)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        root.addView(codeView, wrapParams())
        root.addView(button("نسخ كود الجهاز", 16f, C.GRAY) {
            clip.setPrimaryClip(ClipData.newPlainText("device", code))
            Toast.makeText(this, "تم النسخ", Toast.LENGTH_SHORT).show()
        }, wrapParams())
        root.addView(label("أرسل كود الجهاز لمزوّد البرنامج لتحصل على كود التفعيل، ثم الصقه هنا.", 14f), wrapParams())

        val input = EditText(this).apply {
            hint = "كود التفعيل"
            textSize = 14f
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        root.addView(input, wrapParams())
        root.addView(button("لصق", 16f, C.GRAY) {
            val t = clip.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (!t.isNullOrBlank()) input.setText(t)
        }, wrapParams())
        root.addView(button("تفعيل", 22f, C.GREEN) {
            if (License.activate(this, input.text.toString())) {
                goMain()
            } else {
                Toast.makeText(this, "كود التفعيل غير صحيح لهذا الجهاز", Toast.LENGTH_LONG).show()
            }
        }, wrapParams())

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(C.BG)
        scroll.addView(root)
        setContentView(scroll)
    }
}
