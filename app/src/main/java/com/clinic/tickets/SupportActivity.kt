package com.clinic.tickets

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** صفحة تواصل معنا: بيانات ثابتة منسقة (تُفتح من داخل الإعدادات) */
class SupportActivity : Activity() {

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

    /** رقم مصري محلي (01...) إلى صيغة واتساب الدولية */
    private fun waLink(phone: String) = "https://wa.me/20" + phone.removePrefix("0")

    private fun section(root: LinearLayout, title: String, person: String, phone: String) {
        val card = column()
        card.background = GradientDrawable().apply {
            setColor(0xFFFFFFFF.toInt())
            setStroke(dp(1), 0xFFD5E0EA.toInt())
            cornerRadius = dp(12).toFloat()
        }
        card.addView(label(title, 18f, true, true).apply { setTextColor(C.PRIMARY) }, wrapParams())
        card.addView(label(person, 18f, false, true), wrapParams())
        card.addView(label(phone, 22f, true, true).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR }, wrapParams())
        val actions = row()
        actions.addView(button("اتصال", 16f) { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone))) }, inRow())
        actions.addView(button("واتساب", 16f, C.GREEN) { open(Intent(Intent.ACTION_VIEW, Uri.parse(waLink(phone)))) }, inRow())
        card.addView(actions, wrapParams())
        root.addView(card, wrapParams())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = column()
        root.addView(header("تواصل معنا"), wrapParams())
        root.addView(label("HULUL SYSTEMS", 28f, true, true).apply { setTextColor(C.DARK) }, wrapParams())
        root.addView(label("الشركة المنتجة لبرنامج تذاكر العيادة", 14f, false, true), wrapParams())

        section(root, "المبيعات والتسويق", "المهندس أحمد بدران", "01006773332")
        section(root, "الدعم الفني", "مهندس فتحي بدران", "01016498228")

        root.addView(label("إصدار البرنامج: " + version(), 14f, false, true), wrapParams())
        val codeView = label("كود الجهاز: " + License.deviceCode(this), 13f, false, true)
        codeView.setTextIsSelectable(true)
        root.addView(codeView, wrapParams())
        root.addView(button("رجوع", 18f, C.GRAY) { finish() }, wrapParams())

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(C.BG)
        scroll.addView(root)
        setContentView(scroll)
    }
}
