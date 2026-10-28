package com.clinic.tickets

import android.content.Context
import android.util.Base64
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * ترخيص مرتبط بالجهاز: كود التفعيل توقيع رقمي (ECDSA P-256) لكود الجهاز،
 * والبرنامج يحمل المفتاح العام فقط، فلا يمكن توليد أكواد منه.
 */
object License {
    private const val PUBLIC_KEY =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE2yPUSaeYDJ5thrB3J1gaDAjpC0wHtn0Ex7qOK4FD+KoO7Ixu+mbWe1eIs5atsnTt5HgYL8BgCsBGYMZkwtOovg=="

    private fun prefs(c: Context) = c.getSharedPreferences("license", Context.MODE_PRIVATE)

    /** كود الجهاز: 16 خانة مشتقة من رقم الجهاز، بصيغة XXXX-XXXX-XXXX-XXXX */
    fun deviceCode(c: Context): String {
        val id = android.provider.Settings.Secure.getString(
            c.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val digest = MessageDigest.getInstance("SHA-256").digest(("clinic-tickets:" + id).toByteArray())
        val hex = digest.joinToString("") { "%02X".format(it) }.substring(0, 16)
        return hex.chunked(4).joinToString("-")
    }

    /** تحويل التوقيع الخام (r||s) إلى صيغة DER التي يتوقعها أندرويد */
    private fun derEncode(raw: ByteArray): ByteArray {
        val r = BigInteger(1, raw.copyOfRange(0, 32)).toByteArray()
        val s = BigInteger(1, raw.copyOfRange(32, 64)).toByteArray()
        val body = byteArrayOf(0x02, r.size.toByte()) + r + byteArrayOf(0x02, s.size.toByte()) + s
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    fun verify(c: Context, input: String): Boolean {
        return try {
            val hex = input.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            if (hex.length != 128) return false
            val raw = ByteArray(64) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            val pub = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(Base64.decode(PUBLIC_KEY, Base64.NO_WRAP)))
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(pub)
            sig.update(deviceCode(c).replace("-", "").toByteArray())
            sig.verify(derEncode(raw))
        } catch (e: Exception) {
            false
        }
    }

    fun activate(c: Context, input: String): Boolean {
        if (!verify(c, input)) return false
        prefs(c).edit().putString("code", input.trim()).apply()
        return true
    }

    /** يُعاد التحقق من الكود المحفوظ مع كل تشغيل، فنسخ الإعدادات لجهاز آخر لا ينفع */
    fun isActivated(c: Context): Boolean {
        val saved = prefs(c).getString("code", null) ?: return false
        return verify(c, saved)
    }
}
