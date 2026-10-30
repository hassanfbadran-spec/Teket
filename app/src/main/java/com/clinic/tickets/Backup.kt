package com.clinic.tickets

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** نسخ احتياطي لسجل التذاكر والإعدادات في ملف JSON */
object Backup {

    fun build(c: Context, db: Db): String {
        val root = JSONObject()
        root.put("app", "clinic-tickets")
        root.put("version", 1)

        val s = JSONObject()
        s.put("clinic", Settings.clinic(c))
        s.put("doctor", Settings.doctor(c))
        s.put("footer", Settings.footer(c))
        s.put("line1", Settings.line(c, 1))
        s.put("line2", Settings.line(c, 2))
        s.put("line3", Settings.line(c, 3))
        s.put("paper", Settings.paperDots(c))
        root.put("settings", s)

        val arr = JSONArray()
        for (t in db.allTickets()) {
            val o = JSONObject()
            o.put("day", t.day)
            o.put("number", t.number)
            o.put("type", t.type)
            o.put("issued_at", t.issuedAt)
            o.put("called_at", t.calledAt ?: JSONObject.NULL)
            o.put("status", t.status)
            arr.put(o)
        }
        root.put("tickets", arr)
        return root.toString()
    }

    /** يرجع عدد التذاكر المسترجعة، ويرمي خطأ لو الملف غير صالح */
    fun restore(c: Context, db: Db, text: String): Int {
        val root = JSONObject(text)
        require(root.optString("app") == "clinic-tickets") { "ملف النسخة الاحتياطية غير صالح" }
        val arr = root.getJSONArray("tickets")
        val list = mutableListOf<Ticket>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Ticket(
                    0, o.getString("day"), o.getInt("number"), o.getString("type"),
                    o.getLong("issued_at"),
                    if (o.isNull("called_at")) null else o.getLong("called_at"),
                    o.getString("status")
                )
            )
        }
        db.replaceAll(list)

        val s = root.getJSONObject("settings")
        Settings.save(
            c,
            s.optString("clinic", "العيادة"), s.optString("doctor", ""), s.optString("footer", ""),
            s.optString("line1", ""), s.optString("line2", ""), s.optString("line3", "")
        )
        Settings.savePaper(c, s.optInt("paper", 384))
        return list.size
    }
}
