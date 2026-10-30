package com.clinic.tickets

import android.content.Context

object Settings {
    private fun p(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun clinic(c: Context): String = p(c).getString("clinic", "العيادة") ?: "العيادة"
    fun doctor(c: Context): String = p(c).getString("doctor", "") ?: ""
    fun footer(c: Context): String = p(c).getString("footer", "مع تمنياتنا بالشفاء") ?: ""

    /** أسطر إضافية في آخر التذكرة (1 إلى 3) */
    fun line(c: Context, i: Int): String = p(c).getString("line$i", "") ?: ""

    /** عنوان طابعة البلوتوث المختارة، وفارغ يعني الطابعة المدمجة (Sunmi) */
    fun btAddress(c: Context): String = p(c).getString("bt_addr", "") ?: ""
    fun btName(c: Context): String = p(c).getString("bt_name", "") ?: ""

    /** عرض الورق بالنقاط: 384 (58mm) أو 576 (80mm) */
    fun paperDots(c: Context): Int = p(c).getInt("paper", 384)

    fun save(c: Context, clinic: String, doctor: String, footer: String, l1: String, l2: String, l3: String) {
        p(c).edit()
            .putString("clinic", clinic)
            .putString("doctor", doctor)
            .putString("footer", footer)
            .putString("line1", l1)
            .putString("line2", l2)
            .putString("line3", l3)
            .apply()
    }

    fun saveBt(c: Context, address: String, name: String) {
        p(c).edit().putString("bt_addr", address).putString("bt_name", name).apply()
    }

    fun savePaper(c: Context, dots: Int) {
        p(c).edit().putInt("paper", dots).apply()
    }

    // ---------- كلمة سر الإعدادات ----------
    private fun sha(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun hasPin(c: Context): Boolean = (p(c).getString("pin", "") ?: "").isNotEmpty()
    fun setPin(c: Context, pin: String) {
        p(c).edit().putString("pin", sha("clinic-pin:" + pin)).apply()
    }
    fun clearPin(c: Context) {
        p(c).edit().remove("pin").apply()
    }
    /** كلمة السر الافتراضية للإعدادات هي admin، إلى أن تُغيَّر من داخل الإعدادات */
    fun checkPin(c: Context, pin: String): Boolean {
        val stored = p(c).getString("pin", "") ?: ""
        return if (stored.isEmpty()) pin == "admin" else sha("clinic-pin:" + pin) == stored
    }
}
