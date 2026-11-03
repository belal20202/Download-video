package com.videodl.app

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object Remux {

    /**
     * يعيد كتابة الفيديو بصيغة MP4 القياسية لأندرويد (بدون إعادة ترميز).
     * يرجع true عند النجاح، وfalse عند الفشل (ويُحذف الملف الناتج).
     */
    fun toStandardMp4(src: File, dst: File): Boolean {
        val ex = MediaExtractor()
        var mux: MediaMuxer? = null
        var started = false
        try {
            ex.setDataSource(src.absolutePath)
            val mx = MediaMuxer(dst.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            mux = mx

            val trackMap = HashMap<Int, Int>()
            var hasVideo = false
            for (i in 0 until ex.trackCount) {
                val fmt = ex.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    if (fmt.containsKey(MediaFormat.KEY_ROTATION)) {
                        mx.setOrientationHint(fmt.getInteger(MediaFormat.KEY_ROTATION))
                    }
                    ex.selectTrack(i)
                    trackMap[i] = mx.addTrack(fmt)
                    hasVideo = true
                } else if (mime.startsWith("audio/")) {
                    ex.selectTrack(i)
                    trackMap[i] = mx.addTrack(fmt)
                }
            }
            if (!hasVideo || trackMap.isEmpty()) {
                dst.delete()
                return false
            }

            mx.start()
            started = true

            val buf = ByteBuffer.allocate(8 shl 20)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val track = ex.sampleTrackIndex
                if (track < 0) break
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) break
                val flags =
                    if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, size, maxOf(0L, ex.sampleTime), flags)
                val outTrack = trackMap[track]
                if (outTrack != null) mx.writeSampleData(outTrack, buf, info)
                ex.advance()
            }

            mx.stop()
            started = false
            return dst.exists() && dst.length() > 0
        } catch (e: Exception) {
            dst.delete()
            return false
        } finally {
            try {
                if (started) mux?.stop()
            } catch (_: Exception) {
            }
            try {
                mux?.release()
            } catch (_: Exception) {
            }
            try {
                ex.release()
            } catch (_: Exception) {
            }
        }
    }

    /** اسم ترميز الفيديو داخل الملف، أو نص فارغ إن تعذر. */
    fun videoCodec(path: String): String {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(path)
            var name = ""
            for (i in 0 until ex.trackCount) {
                val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    name = when {
                        mime.contains("avc") -> "H.264"
                        mime.contains("hevc") -> "H.265 (HEVC)"
                        mime.contains("vp9") -> "VP9"
                        mime.contains("vp8") -> "VP8"
                        mime.contains("av01") || mime.contains("av1") -> "AV1"
                        else -> mime
                    }
                    break
                }
            }
            name
        } catch (e: Exception) {
            ""
        } finally {
            try {
                ex.release()
            } catch (_: Exception) {
            }
        }
    }
}
