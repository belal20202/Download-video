package com.videodl.app

import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException

/** يرفض الروابط غير الآمنة: غير https، أو التي تشير إلى عناوين داخل الشبكة المحلية. */
object UrlGuard {

    /** يرجع نصاً يشرح سبب الرفض، أو null إذا كان الرابط مقبولاً. يجب استدعاؤه خارج الخيط الرئيسي. */
    fun check(url: String): String? {
        val u = try {
            URL(url)
        } catch (e: Exception) {
            return "الرابط غير صالح"
        }
        if (!u.protocol.equals("https", ignoreCase = true)) return "يُقبل فقط رابط يبدأ بـ https://"
        val host = u.host?.lowercase().orEmpty()
        if (host.isEmpty()) return "الرابط غير صالح"
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) {
            return "العناوين المحلية غير مسموحة"
        }
        return try {
            val addrs = InetAddress.getAllByName(host)
            if (addrs.any { isPrivate(it) }) "العناوين الداخلية للشبكة غير مسموحة" else null
        } catch (e: UnknownHostException) {
            "تعذر الوصول إلى العنوان. تحقق من الرابط ومن الإنترنت"
        } catch (e: Exception) {
            "تعذر فحص الرابط"
        }
    }

    private fun isPrivate(a: InetAddress): Boolean {
        if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress ||
            a.isSiteLocalAddress || a.isMulticastAddress
        ) return true
        val b = a.address
        if (b.size == 4) {
            val b0 = b[0].toInt() and 0xFF
            val b1 = b[1].toInt() and 0xFF
            if (b0 == 0) return true
            if (b0 == 100 && (b1 and 0xC0) == 64) return true // 100.64.0.0/10
        }
        if (b.size == 16 && (b[0].toInt() and 0xFE) == 0xFC) return true // fc00::/7
        return false
    }
}
