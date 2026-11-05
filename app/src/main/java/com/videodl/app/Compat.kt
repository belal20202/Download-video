package com.videodl.app

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat

/** فحص توافق الملف مع المعرض: الترميز، وقدرة الهاتف على فكه بالعتاد، واختبار فك فعلي. */
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
            for (ci in MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos) {
                if (ci.isEncoder) continue
                if (!ci.supportedTypes.any { it.equals(mime, true) }) continue
                if (!ci.isHardwareAccelerated) continue
                try {
                    if (ci.getCapabilitiesForType(mime).isFormatSupported(test)) return true
                } catch (_: Exception) {
                }
            }
            false
        } catch (e: Exception) {
            true // لا نتهم الملف إذا تعذر الفحص نفسه
        }
    }

    /**
     * اختبار فعلي: هل يفك مفكّك الفيديو العتادي في الهاتف أول إطارات الملف؟ (كما يفعل المعرض)
     * true = نجح، false = فشل، null = تعذر الاختبار نفسه.
     */
    fun hwDecodeTest(path: String): Boolean? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        var stage = 0 // 0 = تهيئة المفكّك، 1 = تغذيته
        try {
            ex.setDataSource(path)
            var idx = -1
            var found: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) {
                    idx = i
                    found = f
                    break
                }
            }
            val fmt = found ?: return null
            if (idx < 0) return null
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null

            val name = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { ci ->
                !ci.isEncoder && ci.isHardwareAccelerated && !ci.isSoftwareOnly &&
                    ci.supportedTypes.any { it.equals(mime, true) }
            }?.name ?: return false

            ex.selectTrack(idx)
            val c = MediaCodec.createByCodecName(name)
            codec = c
            c.configure(fmt, null, null, 0)
            c.start()
            stage = 1

            val bufInfo = MediaCodec.BufferInfo()
            var inputDone = false
            val deadline = System.currentTimeMillis() + 4000
            while (System.currentTimeMillis() < deadline) {
                if (!inputDone) {
                    val ib = c.dequeueInputBuffer(10_000)
                    if (ib >= 0) {
                        val buf = c.getInputBuffer(ib) ?: return null
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            c.queueInputBuffer(ib, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(ib, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val ob = c.dequeueOutputBuffer(bufInfo, 10_000)
                if (ob >= 0) {
                    val got = bufInfo.size > 0
                    val eos = (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    c.releaseOutputBuffer(ob, false)
                    if (got) return true
                    if (eos) break
                }
            }
            return false
        } catch (e: Exception) {
            return if (stage == 0) false else null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                ex.release()
            } catch (_: Exception) {
            }
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
            val vf = vFmt ?: return Report(listOf("لا توجد صورة فيديو في الملف"), "")

            val w = intOf(vf, MediaFormat.KEY_WIDTH)
            val h = intOf(vf, MediaFormat.KEY_HEIGHT)
            val fps = intOf(vf, MediaFormat.KEY_FRAME_RATE)
            val hw = hwSupports(vMime, w, h, fps)
            val real = hwDecodeTest(path)

            val problems = ArrayList<String>()
            if (!(vMime.contains("avc") || vMime.contains("hevc"))) {
                problems += "ترميز الفيديو ${videoName(vMime)} غير مناسب للمعرض"
            } else if (!hw) {
                problems += "الهاتف لا يفك هذا الفيديو بالعتاد (${w}×${h})"
            } else if (real == false) {
                problems += "فشل اختبار فك الفيديو بالعتاد على هذا الهاتف"
            }
            if (aMime.contains("opus") || aMime.contains("vorbis")) {
                problems += "ترميز الصوت ${audioName(aMime)} غير مناسب للمعرض"
            }

            val summary = buildString {
                append("الترميز: ").append(videoName(vMime))
                if (aMime.isNotEmpty()) append(" + ").append(audioName(aMime))
                if (fps > 0) append(" • ").append(fps).append("fps")
                append("\nفك بالعتاد: ").append(if (hw) "ممكن" else "غير ممكن")
                append(" • الاختبار الفعلي: ")
                append(
                    when (real) {
                        true -> "نجح ✓"
                        false -> "فشل ✗"
                        null -> "غير معروف"
                    }
                )
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
