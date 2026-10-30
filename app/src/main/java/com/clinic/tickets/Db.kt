package com.clinic.tickets

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Types {
    const val EXAM = "كشف عادي"
    const val URGENT = "كشف مستعجل"
    const val REVIEW = "مراجعة"
    const val CONSULT = "استشارة"
    const val LEGACY_EXAM = "كشف" // تذاكر قديمة قبل تغيير الاسم، تُحسب كشف عادي
}

data class Ticket(
    val id: Long,
    val day: String,
    val number: Int,
    val type: String,
    val issuedAt: Long,
    val calledAt: Long?,
    val status: String // waiting / called / done / skipped
)

data class Stat(
    val label: String,
    val total: Int,
    val exam: Int,
    val urgent: Int,
    val review: Int,
    val consult: Int,
    val skipped: Int,
    val cancelled: Int,
    val avgWaitMin: Int
)

fun statusAr(s: String) = when (s) {
    "waiting" -> "انتظار"
    "called" -> "جاري"
    "done" -> "تم"
    "skipped" -> "تخطي"
    "cancelled" -> "ملغاة"
    else -> s
}

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "tickets.db", null, 1) {

    private val cols = "id,day,number,type,issued_at,called_at,status"

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tickets(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "day TEXT NOT NULL," +
                "number INTEGER NOT NULL," +
                "type TEXT NOT NULL," +
                "issued_at INTEGER NOT NULL," +
                "called_at INTEGER," +
                "status TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_day ON tickets(day)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    companion object {
        fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun map(c: Cursor) = Ticket(
        c.getLong(0), c.getString(1), c.getInt(2), c.getString(3),
        c.getLong(4), if (c.isNull(5)) null else c.getLong(5), c.getString(6)
    )

    /** يصدر تذكرة جديدة بتسلسل واحد لليوم، ويرجع التذكرة وعدد المنتظرين قبلها */
    @Synchronized
    fun issue(type: String): Pair<Ticket, Int> {
        val db = writableDatabase
        val day = today()
        var n = 1
        db.rawQuery("SELECT COALESCE(MAX(number),0) FROM tickets WHERE day=?", arrayOf(day)).use {
            if (it.moveToFirst()) n = it.getInt(0) + 1
        }
        val now = System.currentTimeMillis()
        val v = ContentValues().apply {
            put("day", day)
            put("number", n)
            put("type", type)
            put("issued_at", now)
            put("status", "waiting")
        }
        val id = db.insert("tickets", null, v)
        return Ticket(id, day, n, type, now, null, "waiting") to waitingAhead(day, n, type)
    }

    /**
     * المنتظرون قبل هذه التذكرة في ترتيب الاستدعاء:
     * المستعجل يسبقه المستعجلون الأقدم فقط، وغيره يسبقه كل المستعجلين المنتظرين والأرقام الأقل.
     */
    private fun waitingAhead(day: String, number: Int, type: String): Int {
        val sql: String
        val args: Array<String>
        if (type == Types.URGENT) {
            sql = "SELECT COUNT(*) FROM tickets WHERE day=? AND status='waiting' AND type=? AND number<?"
            args = arrayOf(day, Types.URGENT, number.toString())
        } else {
            sql = "SELECT COUNT(*) FROM tickets WHERE day=? AND status='waiting' AND (type=? OR number<?)"
            args = arrayOf(day, Types.URGENT, number.toString())
        }
        return readableDatabase.rawQuery(sql, args).use { it.moveToFirst(); it.getInt(0) }
    }

    fun waitingTotal(): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM tickets WHERE day=? AND status='waiting'", arrayOf(today())
        ).use { it.moveToFirst(); it.getInt(0) }

    fun lastNumber(): Int =
        readableDatabase.rawQuery(
            "SELECT COALESCE(MAX(number),0) FROM tickets WHERE day=?", arrayOf(today())
        ).use { it.moveToFirst(); it.getInt(0) }

    fun current(): Ticket? =
        readableDatabase.rawQuery(
            "SELECT $cols FROM tickets WHERE day=? AND status='called' ORDER BY called_at DESC LIMIT 1",
            arrayOf(today())
        ).use { if (it.moveToFirst()) map(it) else null }

    /** ينهي التذكرة الحالية ويستدعي المستعجل أولاً، وإلا أقل رقم منتظر (ما سبق استدعاؤه يتخطاه تلقائياً) */
    @Synchronized
    fun callNext(): Ticket? {
        val db = writableDatabase
        val day = today()
        db.execSQL("UPDATE tickets SET status='done' WHERE day=? AND status='called'", arrayOf<Any>(day))
        val next = db.rawQuery(
            "SELECT $cols FROM tickets WHERE day=? AND status='waiting' " +
                "ORDER BY CASE WHEN type=? THEN 0 ELSE 1 END, number LIMIT 1",
            arrayOf(day, Types.URGENT)
        ).use { if (it.moveToFirst()) map(it) else null } ?: return null
        db.execSQL(
            "UPDATE tickets SET status='called', called_at=? WHERE id=?",
            arrayOf<Any>(System.currentTimeMillis(), next.id)
        )
        return current()
    }

    /** يتخطى التذكرة الحالية ويستدعي التالية */
    @Synchronized
    fun skipCurrent(): Ticket? {
        writableDatabase.execSQL(
            "UPDATE tickets SET status='skipped' WHERE day=? AND status='called'", arrayOf<Any>(today())
        )
        return callNext()
    }

    fun ticketsOfDay(day: String): List<Ticket> {
        val out = mutableListOf<Ticket>()
        readableDatabase.rawQuery(
            "SELECT $cols FROM tickets WHERE day=? ORDER BY number", arrayOf(day)
        ).use { while (it.moveToNext()) out.add(map(it)) }
        return out
    }

    fun aheadOf(t: Ticket): Int = waitingAhead(t.day, t.number, t.type)

    /** آخر تذكرة صدرت اليوم وغير ملغاة */
    fun lastIssued(): Ticket? =
        readableDatabase.rawQuery(
            "SELECT $cols FROM tickets WHERE day=? AND status<>'cancelled' ORDER BY id DESC LIMIT 1",
            arrayOf(today())
        ).use { if (it.moveToFirst()) map(it) else null }

    /** يلغي تذكرة منتظرة بالرقم، أو آخر تذكرة لو الرقم null. يرجع رسالة للعرض */
    @Synchronized
    fun cancel(number: Int?): String {
        val db = writableDatabase
        val t = if (number == null) {
            lastIssued()
        } else {
            db.rawQuery(
                "SELECT $cols FROM tickets WHERE day=? AND number=?", arrayOf(today(), number.toString())
            ).use { if (it.moveToFirst()) map(it) else null }
        }
        if (t == null) return "لا توجد تذكرة بهذا الرقم اليوم"
        if (t.status == "cancelled") return "التذكرة رقم ${t.number} ملغاة بالفعل"
        if (t.status != "waiting") return "لا يمكن إلغاء التذكرة رقم ${t.number} لأنها استُدعيت"
        db.execSQL("UPDATE tickets SET status='cancelled' WHERE id=?", arrayOf<Any>(t.id))
        return "تم إلغاء التذكرة رقم ${t.number}"
    }

    fun allTickets(): List<Ticket> {
        val out = mutableListOf<Ticket>()
        readableDatabase.rawQuery("SELECT $cols FROM tickets ORDER BY id", null)
            .use { while (it.moveToNext()) out.add(map(it)) }
        return out
    }

    /** استبدال كل التذاكر (للاسترجاع من نسخة احتياطية) */
    @Synchronized
    fun replaceAll(list: List<Ticket>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM tickets")
            for (t in list) {
                val v = ContentValues()
                v.put("day", t.day)
                v.put("number", t.number)
                v.put("type", t.type)
                v.put("issued_at", t.issuedAt)
                if (t.calledAt != null) v.put("called_at", t.calledAt) else v.putNull("called_at")
                v.put("status", t.status)
                db.insert("tickets", null, v)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * إحصائيات لفترة تبدأ بـ prefix (yyyy أو yyyy-MM أو yyyy-MM-dd).
     * groupLen = 0 يرجع صفاً واحداً للإجمالي، 10 تجميع بالأيام، 7 تجميع بالشهور.
     */
    fun stats(prefix: String, groupLen: Int): List<Stat> {
        val sql = "SELECT substr(day,1,$groupLen) g, " +
            "SUM(CASE WHEN status<>'cancelled' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN status<>'cancelled' AND type IN (?,?) THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN status<>'cancelled' AND type=? THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN status<>'cancelled' AND type=? THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN status<>'cancelled' AND type=? THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN status='skipped' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN status='cancelled' THEN 1 ELSE 0 END), " +
            "AVG(CASE WHEN called_at IS NOT NULL THEN called_at-issued_at END) " +
            "FROM tickets WHERE day LIKE ? GROUP BY g ORDER BY g"
        val args = arrayOf(Types.EXAM, Types.LEGACY_EXAM, Types.URGENT, Types.REVIEW, Types.CONSULT, "$prefix%")
        val out = mutableListOf<Stat>()
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Stat(
                        c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5),
                        c.getInt(6), c.getInt(7),
                        if (c.isNull(8)) 0 else (c.getDouble(8) / 60000.0).toInt()
                    )
                )
            }
        }
        return out
    }
}
