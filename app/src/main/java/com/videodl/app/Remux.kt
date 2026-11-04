package com.videodl.app

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object Remux {

    /**
     * يعيد كتابة الفيديو بصيغة MP4 القياسية لأندرويد (بدون إعادة ترميز) مع تصفير بداية التوقيت.
     * يرجع null عند النجاح، أو نصاً قصيراً يشرح سبب الفشل.
     */
    fun toStandardMp4(src: File, dst: File): String? {
        // المرحلة 1: إيجاد أصغر زمن في الملف
        var minUs = Long.MAX_VALUE
        val scan = MediaExtractor()
        try {
            scan.setDataSource(src.absolutePath)
            for (i in 0 until scan.trackCount) {
                val mime = scan.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) scan.selectTrack(i)
            }
            while (scan.sampleTrackIndex >= 0) {
                val t = scan.sampleTime
                if (t < minUs) minUs = t
                if (!scan.advance()) break
            }
        } catch (e: Exception) {
            return "قراءة الملف"
        } finally {
            try {
                scan.release()
            } catch (_: Exception) {
            }
        }
        val base = if (minUs == Long.MAX_VALUE) 0L else minUs

        // المرحلة 2: النسخ إلى حاوية جديدة
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
            if (!hasVideo) {
                dst.delete()
                return "لا توجد صورة فيديو"
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
                val t = (ex.sampleTime - base).coerceAtLeast(0L)
                info.set(0, size, t, flags)
                val outTrack = trackMap[track]
                if (outTrack != null) mx.writeSampleData(outTrack, buf, info)
                ex.advance()
            }

            mx.stop()
            started = false
            return if (dst.exists() && dst.length() > 0) null else "ملف فارغ"
        } catch (e: Exception) {
            dst.delete()
            return "الكتابة: ${e.javaClass.simpleName}"
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
}
