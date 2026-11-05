package com.videodl.app

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * ينقل فهرس الملف (moov) إلى بدايته بدون إعادة ترميز. الصورة والصوت لا يتغيران.
 * النتيجة: "ok" (أُنشئ الملف الجديد) أو "already" (الفهرس في البداية أصلاً) أو نص يشرح سبب الفشل.
 */
object Faststart {

    private class Box(val type: String, val start: Long, val size: Long, val hdr: Int)

    private val CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl")

    fun toFront(src: File, dst: File): String {
        try {
            RandomAccessFile(src, "r").use { f ->
                val len = f.length()
                val boxes = topBoxes(f, len)
                if (boxes.any { it.type == "moof" }) return "fragmented"
                val moov = boxes.firstOrNull { it.type == "moov" } ?: return "no-moov"
                val mdat = boxes.firstOrNull { it.type == "mdat" } ?: return "no-mdat"
                if (moov.start < mdat.start) return "already"
                if (moov.size > 256L * 1024 * 1024) return "moov-too-big"

                // نقطة الإدخال: بعد ftyp مباشرة
                val ins = if (boxes[0].type == "ftyp") boxes[0].start + boxes[0].size else 0L

                val mbuf = ByteArray(moov.size.toInt())
                f.seek(moov.start)
                f.readFully(mbuf)
                patch(ByteBuffer.wrap(mbuf), moov.hdr, mbuf.size, moov.size)

                FileOutputStream(dst).use { o ->
                    copyRange(f, 0L, ins, o)
                    o.write(mbuf)
                    copyRange(f, ins, moov.start, o)
                    copyRange(f, moov.start + moov.size, len, o)
                }
                if (dst.length() != len) {
                    dst.delete()
                    return "size-mismatch"
                }
            }
            return "ok"
        } catch (e: Exception) {
            dst.delete()
            return "error:${e.javaClass.simpleName}"
        }
    }

    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun u64(b: ByteArray, o: Int): Long = (u32(b, o) shl 32) or u32(b, o + 4)

    private fun topBoxes(f: RandomAccessFile, len: Long): List<Box> {
        val out = ArrayList<Box>()
        val h = ByteArray(16)
        var pos = 0L
        while (pos + 8 <= len) {
            f.seek(pos)
            f.readFully(h, 0, 8)
            var size = u32(h, 0)
            val type = String(h, 4, 4, Charsets.ISO_8859_1)
            var hdr = 8
            if (size == 1L) {
                f.readFully(h, 8, 8)
                size = u64(h, 8)
                hdr = 16
            } else if (size == 0L) {
                size = len - pos
            }
            if (size < hdr || pos + size > len) throw IllegalStateException("bad box")
            out += Box(type, pos, size, hdr)
            pos += size
        }
        return out
    }

    /** يضيف delta إلى كل مواضع البيانات (stco / co64) داخل moov. */
    private fun patch(bb: ByteBuffer, start: Int, end: Int, delta: Long) {
        var p = start
        while (p + 8 <= end) {
            var size = bb.getInt(p).toLong() and 0xFFFFFFFFL
            val type = String(ByteArray(4) { bb.get(p + 4 + it) }, Charsets.ISO_8859_1)
            var hdr = 8
            if (size == 1L) {
                size = bb.getLong(p + 8)
                hdr = 16
            } else if (size == 0L) {
                size = (end - p).toLong()
            }
            if (size < hdr || p + size > end) throw IllegalStateException("bad inner box")
            val boxEnd = (p + size).toInt()
            when {
                type in CONTAINERS -> patch(bb, p + hdr, boxEnd, delta)
                type == "stco" -> {
                    val n = bb.getInt(p + hdr + 4)
                    var q = p + hdr + 8
                    if (q.toLong() + 4L * n > boxEnd) throw IllegalStateException("bad stco")
                    repeat(n) {
                        val v = (bb.getInt(q).toLong() and 0xFFFFFFFFL) + delta
                        if (v > 0xFFFFFFFFL) throw IllegalStateException("stco overflow")
                        bb.putInt(q, v.toInt())
                        q += 4
                    }
                }
                type == "co64" -> {
                    val n = bb.getInt(p + hdr + 4)
                    var q = p + hdr + 8
                    if (q.toLong() + 8L * n > boxEnd) throw IllegalStateException("bad co64")
                    repeat(n) {
                        bb.putLong(q, bb.getLong(q) + delta)
                        q += 8
                    }
                }
            }
            p = boxEnd
        }
    }

    private fun copyRange(f: RandomAccessFile, from: Long, to: Long, out: FileOutputStream) {
        if (to <= from) return
        f.seek(from)
        val buf = ByteArray(1 shl 20)
        var remain = to - from
        while (remain > 0) {
            val n = f.read(buf, 0, minOf(buf.size.toLong(), remain).toInt())
            if (n < 0) throw IllegalStateException("unexpected end")
            out.write(buf, 0, n)
            remain -= n
        }
    }
}
