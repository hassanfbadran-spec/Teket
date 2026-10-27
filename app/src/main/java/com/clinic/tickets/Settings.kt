package com.clinic.tickets

import android.content.Context

object Settings {
    private fun p(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun clinic(c: Context): String = p(c).getString("clinic", "العيادة") ?: "العيادة"
    fun doctor(c: Context): String = p(c).getString("doctor", "") ?: ""
    fun footer(c: Context): String = p(c).getString("footer", "مع تمنياتنا بالشفاء") ?: ""

    fun save(c: Context, clinic: String, doctor: String, footer: String) {
        p(c).edit()
            .putString("clinic", clinic)
            .putString("doctor", doctor)
            .putString("footer", footer)
            .apply()
    }
}
