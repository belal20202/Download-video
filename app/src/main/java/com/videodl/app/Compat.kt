package com.videodl.app

import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat

/** فحص توافق الملف مع المعرض: الترميز، وهل الهاتف يفك الفيديو بالعتاد. */
object Compat {

    class Report(val problems: List<String>, val summary: String)

    private fun videoName(mime: String) = when {
        mime.contains("avc") -> "H.264"
        mime.contains("hevc") -> "H.265"
        mime.contains("vp9") -> "VP9"
        mime.contains("vp8") -> "VP8"
        mime.contains("av01") || mime.contains("av1") -> "AV1"
        else -> mime.removePrefix("video/")
    }

    private fun audioName(mime: String) = when {
        mime.contains("mp4a") -> "AAC"
        mime.contains("opus") -> "Opus"
        mime.contains("vorbis") -> "Vorbis"
        mime.contains("mpeg") -> "MP3"
        mime.contains("ac3") -> "AC3"
        else -> mime.removePrefix("audio/")
    }

    private fun intOf(f: MediaFormat, key: String): Int = try {
        if (f.containsKey(key)) f.getInteger(key) else 0
    } catch (e: Exception) {
        try {
            f.getFloat(key).toInt()
        } catch (e2: Exception) {
            0
        }
    }

    private fun hwSupports(mime: String, w: Int, h: Int, fps: Int): Boolean {
        return try {
            val test = MediaFormat.createVideoFormat(mime, w, h)
            if (fps > 0) test.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            for (info in MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos) {
                if (info.isEncoder) continue
                if (!info.supportedTypes.any { it.equals(mime, true) }) continue
                if (!info.isHardwareAccelerated) continue
                try {
                    if (info.getCapabilitiesForType(mime).isFormatSupported(test)) return true
                } catch (_: Exception) {
                }
            }
            false
        } catch (e: Exception) {
            true // لا نتهم الملف إذا تعذر الفحص نفسه
        }
    }

    fun check(path: String): Report {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(path)
            var vMime = ""
            var aMime = ""
            var vFmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("video/") && vFmt == null) {
                    vMime = m
                    vFmt = f
                } else if (m.startsWith("audio/") && aMime.isEmpty()) {
                    aMime = m
                }
            }
            if (vFmt == null) return Report(listOf("لا توجد صورة فيديو في الملف"), "")

            val w = intOf(vFmt, MediaFormat.KEY_WIDTH)
            val h = intOf(vFmt, MediaFormat.KEY_HEIGHT)
            val fps = intOf(vFmt, MediaFormat.KEY_FRAME_RATE)
            val hw = hwSupports(vMime, w, h, fps)

            val problems = ArrayList<String>()
            if (!(vMime.contains("avc") || vMime.contains("hevc"))) {
                problems += "ترميز الفيديو ${videoName(vMime)} غير مناسب للمعرض"
            } else if (!hw) {
                problems += "الهاتف لا يفك هذا الفيديو بالعتاد (${w}×${h})"
            }
            if (aMime.contains("opus") || aMime.contains("vorbis")) {
                problems += "ترميز الصوت ${audioName(aMime)} غير مناسب للمعرض"
            }

            val summary = buildString {
                append("الترميز: ").append(videoName(vMime))
                if (aMime.isNotEmpty()) append(" + ").append(audioName(aMime))
                if (fps > 0) append(" • ").append(fps).append("fps")
                append(" • فك بالعتاد: ").append(if (hw) "نعم" else "لا")
            }
            return Report(problems, summary)
        } catch (e: Exception) {
            return Report(emptyList(), "")
        } finally {
            try {
                ex.release()
            } catch (_: Exception) {
            }
        }
    }
}
