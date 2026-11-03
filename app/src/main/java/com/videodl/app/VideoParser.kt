package com.videodl.app

import org.json.JSONObject
import java.util.Locale
import kotlin.math.min

data class Quality(
    val label: String,
    val res: Int,
    val formatId: String,
    val audioId: String?,
    val sizeBytes: Long,
    val isAudioOnly: Boolean = false
)

data class VideoMeta(
    val title: String,
    val uploader: String,
    val platform: String,
    val durationSec: Long,
    val thumbnail: String,
    val qualities: List<Quality>
)

private class Fmt(
    val id: String,
    val ext: String,
    val vcodec: String,
    val acodec: String,
    val width: Int,
    val height: Int,
    val tbr: Double,
    val abr: Double,
    val size: Long
) {
    val res: Int get() = if (width > 0 && height > 0) min(width, height) else height
}

private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")
private fun JSONObject.dbl(k: String): Double = if (isNull(k)) 0.0 else optDouble(k, 0.0)

// 3 = H.264 (الأكثر توافقاً) ، 2 = HEVC/غير معروف ، 1 = VP9 ، 0 = AV1 (لا تعمل على كثير من الهواتف)
private fun codecScore(v: String): Int {
    val c = v.lowercase()
    return when {
        c.startsWith("avc") || c.startsWith("h264") -> 3
        c.startsWith("hev") || c.startsWith("hvc") || c.startsWith("h265") || c.startsWith("bytevc1") -> 2
        c.startsWith("av0") || c.startsWith("av1") -> 0
        c.startsWith("vp") -> 1
        else -> 2
    }
}

private fun estimate(f: Fmt, duration: Double): Long = when {
    f.size > 0 -> f.size
    f.tbr > 0 && duration > 0 -> (f.tbr * 1000.0 / 8.0 * duration).toLong()
    else -> 0L
}

private fun resLabel(h: Int): String = when {
    h >= 2160 -> "${h}p • 4K"
    h >= 1440 -> "${h}p • 2K"
    h >= 1080 -> "${h}p • Full HD"
    h >= 720 -> "${h}p • HD"
    else -> "${h}p"
}

object VideoParser {
    fun parse(raw: String): VideoMeta {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "لم يتم العثور على بيانات الفيديو" }
        var root = JSONObject(raw.substring(start, end + 1))
        if (!root.has("formats")) {
            val first = root.optJSONArray("entries")?.optJSONObject(0)
            if (first != null) root = first
        }

        val duration = root.dbl("duration")
        val fmts = ArrayList<Fmt>()
        val arr = root.optJSONArray("formats")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.str("format_id")
                if (id.isEmpty()) continue
                val size = when {
                    !o.isNull("filesize") -> o.dbl("filesize").toLong()
                    !o.isNull("filesize_approx") -> o.dbl("filesize_approx").toLong()
                    else -> 0L
                }
                fmts += Fmt(
                    id = id,
                    ext = o.str("ext"),
                    vcodec = o.str("vcodec"),
                    acodec = o.str("acodec"),
                    width = o.dbl("width").toInt(),
                    height = o.dbl("height").toInt(),
                    tbr = o.dbl("tbr"),
                    abr = o.dbl("abr"),
                    size = size
                )
            }
        }

        val vids = fmts.filter { it.vcodec != "none" && it.height > 0 && it.ext != "mhtml" }
        val auds = fmts.filter { it.vcodec == "none" && it.acodec != "none" && it.acodec.isNotEmpty() }

        val bestAudio = auds.sortedWith(
            compareByDescending<Fmt> {
                if (it.acodec.lowercase().startsWith("mp4a") || it.ext == "m4a") 1 else 0
            }.thenByDescending { maxOf(it.abr, it.tbr) }
        ).firstOrNull()

        // نستبعد الترميزات غير المتوافقة (AV1/VP9) إذا وُجد H.264 أو HEVC
        val good = vids.filter { codecScore(it.vcodec) >= 2 }
        val pool = if (good.isNotEmpty()) good
        else vids.filter { codecScore(it.vcodec) >= 1 }.ifEmpty { vids }

        val qualities = ArrayList<Quality>()
        pool.groupBy { it.res }.keys.sortedDescending().forEach { res ->
            val v = pool.filter { it.res == res }
                .sortedWith(compareByDescending<Fmt> { codecScore(it.vcodec) }.thenByDescending { it.tbr })
                .first()
            val hasAudio = v.acodec != "none"
            val a = if (hasAudio) null else bestAudio
            val size = estimate(v, duration) + (if (a != null) estimate(a, duration) else 0L)
            qualities += Quality(resLabel(res), res, v.id, a?.id, size)
        }

        val audioSource = bestAudio ?: vids.filter { it.acodec != "none" }.maxByOrNull { it.tbr }
        if (audioSource != null) {
            qualities += Quality(
                label = "صوت فقط (MP3)",
                res = 0,
                formatId = audioSource.id,
                audioId = null,
                sizeBytes = (duration * 16000.0).toLong(),
                isAudioOnly = true
            )
        }

        if (qualities.isEmpty()) {
            qualities += Quality("الجودة الأصلية", 0, "best", null, 0L)
        }

        return VideoMeta(
            title = root.str("title").ifEmpty { "فيديو" },
            uploader = root.str("uploader").ifEmpty { root.str("channel") },
            platform = root.str("extractor_key"),
            durationSec = duration.toLong(),
            thumbnail = root.str("thumbnail"),
            qualities = qualities
        )
    }
}

fun fmtDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun fmtSize(b: Long): String = when {
    b <= 0 -> ""
    b >= 1_073_741_824L -> String.format(Locale.US, "%.2f GB", b / 1_073_741_824.0)
    else -> String.format(Locale.US, "%.1f MB", b / 1_048_576.0)
}
