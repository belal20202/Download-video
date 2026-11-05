package com.videodl.app

import android.app.Application
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class Probe(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val hasAudio: Boolean,
    val frameOk: Boolean
)

internal class Attempt(
    val file: File,
    val probe: Probe,
    val report: Compat.Report?,
    val problems: List<String>,
    val prep: String
)

internal class Saved(val uri: Uri, val mime: String, val info: String, val needsCompat: Boolean)

// نسخة أكثر توافقاً: H.264 + AAC حتى 720p و30 إطار
private const val SAFE_FORMAT =
    "bv*[vcodec^=avc1][height<=?720][fps<=?30]+ba[acodec^=mp4a]/" +
        "b[vcodec^=avc1][acodec^=mp4a][height<=?720]/" +
        "b[ext=mp4][height<=?720]/b[ext=mp4]"

private const val MAX_THUMB_BYTES = 4 * 1024 * 1024

/** حالة التطبيق: كائن واحد يعيش مع العملية (وليس مع الشاشة)، فلا يضيع التحميل عند الخروج. */
class MainVM(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var url by mutableStateOf("")
        private set
    var meta by mutableStateOf<VideoMeta?>(null)
    var thumb by mutableStateOf<ImageBitmap?>(null)
    var selectedIndex by mutableStateOf(0)
    var status by mutableStateOf("")
    var isError by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var etaText by mutableStateOf("")
    var busy by mutableStateOf(false)
    var downloading by mutableStateOf(false)
    var dua by mutableStateOf("")
    var savedUri by mutableStateOf<Uri?>(null)
    var savedMime by mutableStateOf("video/mp4")
    var offerCompat by mutableStateOf(false)

    private val processId = "videodl-main"

    @Volatile
    private var cancelled = false

    // الرابط الذي تم التعرف عليه فعلاً: التحميل يستخدمه هو فقط
    private var fetchedUrl = ""
    private var fetchId = 0
    private var lastNotify = 0L

    // https فقط
    private fun extractUrl(text: String): String? =
        Regex("https://\\S+", RegexOption.IGNORE_CASE).find(text.trim())?.value

    private fun awaitEngine() {
        App.initThread.join()
        App.initError?.let { throw IllegalStateException("تعذر تشغيل محرك التحميل: $it") }
    }

    // ---------------------------------------------------------------- الرابط
    fun onUrlChange(v: String) {
        url = v
        // إذا تغيّر الرابط عن الذي تم التعرف عليه، نخفي بطاقة الفيديو القديمة
        if (!busy && !downloading && meta != null && extractUrl(v) != fetchedUrl) {
            fetchId++
            meta = null
            thumb = null
            offerCompat = false
            savedUri = null
            progress = 0f
            dua = ""
            isError = false
            status = ""
        }
    }

    /** رابط وصل عبر «مشاركة» من تطبيق آخر. */
    fun receiveShared(text: String) {
        val link = extractUrl(text)
        if (link == null) {
            isError = true
            status = "الرابط المُشارَك غير مقبول (يجب أن يبدأ بـ https://)"
            return
        }
        if (busy || downloading) {
            isError = true
            status = "انتظر انتهاء العملية الحالية ثم شارك الرابط مرة أخرى"
            return
        }
        onUrlChange(link)
        fetch()
    }

    // ---------------------------------------------------------------- التعرف على الفيديو
    fun fetch() {
        if (busy || downloading) return
        val u = extractUrl(url)
        if (u == null) {
            isError = true
            status = "الصق رابطاً صحيحاً يبدأ بـ https://"
            return
        }
        val id = ++fetchId
        scope.launch {
            busy = true
            isError = false
            meta = null
            thumb = null
            savedUri = null
            offerCompat = false
            progress = 0f
            dua = ""
            status = "جارٍ التعرف على الفيديو..."
            try {
                val parsed = withContext(Dispatchers.IO) {
                    awaitEngine()
                    UrlGuard.check(u)?.let { throw IllegalStateException(it) }
                    val req = YoutubeDLRequest(u).apply {
                        addOption("--dump-single-json")
                        addOption("--no-playlist")
                        addOption("--no-warnings")
                    }
                    VideoParser.parse(YoutubeDL.getInstance().execute(req).out)
                }
                fetchedUrl = u
                meta = parsed
                // الافتراضي: أعلى دقة حتى 1080p بنسخة عادية (ليست 60fps)
                selectedIndex = parsed.qualities
                    .indexOfFirst { !it.isAudioOnly && !it.highFps && it.res in 1..1080 }
                    .let { if (it >= 0) it else 0 }
                status = "تم التعرف على الفيديو. اختر الجودة ثم اضغط تحميل"
                if (parsed.thumbnail.isNotEmpty()) {
                    launch(Dispatchers.IO) {
                        val bmp = loadThumb(parsed.thumbnail)
                        if (id == fetchId) thumb = bmp
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                isError = true
                status = friendly(e)
            }
            busy = false
        }
    }

    // ---------------------------------------------------------------- التحميل
    fun download() = startDownload(compat = false)

    fun downloadCompatible() = startDownload(compat = true)

    private fun startDownload(compat: Boolean) {
        val m = meta ?: return
        val q = m.qualities.getOrNull(selectedIndex) ?: return
        val u = fetchedUrl
        if (u.isEmpty() || downloading || busy) return
        val previous = if (compat) savedUri else null
        cancelled = false
        scope.launch {
            downloading = true
            isError = false
            progress = 0f
            etaText = ""
            offerCompat = false
            if (!compat) savedUri = null
            dua = Duas.next()
            status = if (compat) "جارٍ تنزيل نسخة متوافقة (H.264 حتى 720p)..."
            else "جارٍ التحميل بجودة ${q.label}..."
            DownloadService.start(app)
            var ok = false
            var finalText = ""
            try {
                val saved = withContext(Dispatchers.IO) { runDownload(u, q, m.title, compat) }
                savedUri = saved.uri
                savedMime = saved.mime
                progress = 1f
                val folder = if (q.isAudioOnly) "Music/VideoDL" else "Movies/VideoDL"
                status = "تم الحفظ ✅ في: $folder (يظهر في المعرض)\n${saved.info}"
                offerCompat = saved.needsCompat
                if (previous != null) {
                    // النسخة المتوافقة نجحت: نحذف الملف القديم الذي أنشأناه نحن
                    try {
                        app.contentResolver.delete(previous, null, null)
                    } catch (_: Exception) {
                    }
                }
                ok = true
                finalText = m.title
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                progress = 0f
                offerCompat = compat && savedUri != null
                if (cancelled) {
                    status = "تم إلغاء التحميل"
                } else {
                    isError = true
                    status = friendly(e)
                    finalText = status.lineSequence().first().take(90)
                }
            } finally {
                downloading = false
                etaText = ""
                DownloadService.stop(app)
                if (!cancelled) DownloadNotifier.finished(app, ok, finalText)
                Thread { cleanTemp() }.start()
            }
        }
    }

    fun cancel() {
        cancelled = true
        try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (_: Exception) {
        }
    }

    private fun cleanTemp() {
        try {
            File(app.cacheDir, "dl").deleteRecursively()
        } catch (_: Exception) {
        }
    }

    private fun pushProgress() {
        val now = System.currentTimeMillis()
        if (now - lastNotify < 1000) return
        lastNotify = now
        val pct = (progress * 100).toInt()
        DownloadNotifier.progress(app, pct, if (etaText.isNotEmpty()) "$pct% • $etaText" else "$pct%")
    }

    private fun checkSpace(q: Quality) {
        val need = if (q.sizeBytes > 0) (q.sizeBytes * 2.5).toLong() else 300L * 1024 * 1024
        val free = app.cacheDir.usableSpace
        if (free < need) {
            throw IllegalStateException(
                "المساحة الفارغة غير كافية (يلزم ≈ ${fmtSize(need)} والمتوفر ${fmtSize(free)})"
            )
        }
    }

    private fun runDownload(u: String, q: Quality, title: String, compat: Boolean): Saved {
        awaitEngine()
        UrlGuard.check(u)?.let { throw IllegalStateException(it) }
        checkSpace(q)

        val a = attempt(u, q, File(app.cacheDir, "dl"), compat)
        val f = a.file
        val p = a.probe

        val mime = when (f.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            else -> "application/octet-stream"
        }
        val isAudio = mime.startsWith("audio")
        val uri = saveToMediaStore(f, mime, isAudio, title)
            ?: throw IllegalStateException("تعذر حفظ الملف على الجهاز")

        val dur = fmtDuration(p.durationMs / 1000)
        val sb = StringBuilder()
        if (q.isAudioOnly) {
            sb.append("ملف صوتي • المدة ").append(dur)
        } else {
            sb.append("الدقة الفعلية ${p.width}×${p.height} • المدة ").append(dur)
            a.report?.let { if (it.summary.isNotEmpty()) sb.append("\n").append(it.summary) }
            sb.append("\nتجهيز الملف: ").append(a.prep)
            if (compat) sb.append("\n↺ نسخة متوافقة (H.264 حتى 720p)")
            if (a.problems.isNotEmpty()) {
                sb.append("\n⚠ ").append(a.problems.joinToString("، "))
                sb.append("\nيمكنك مشاهدته بزر «تشغيل الملف»")
                if (!compat) sb.append("، أو تنزيل نسخة متوافقة مع المعرض بالزر أدناه")
            }
            if (!p.hasAudio) sb.append("\n⚠ هذا الفيديو بدون صوت")
        }
        val needsCompat = !compat && !q.isAudioOnly && a.problems.isNotEmpty()
        return Saved(uri, mime, sb.toString(), needsCompat)
    }

    /** ينزّل الملف ويجهّزه ويفحصه. safe = نسخة H.264/AAC حتى 720p. */
    private fun attempt(u: String, q: Quality, dir: File, safe: Boolean): Attempt {
        dir.deleteRecursively()
        dir.mkdirs()

        val req = YoutubeDLRequest(u).apply {
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--retries", "10")
            addOption("--fragment-retries", "10")
            // اسم مؤقت بسيط لتجنب مشاكل الرموز في الأسماء
            addOption("-o", "${dir.absolutePath}/video.%(ext)s")
            if (q.isAudioOnly) {
                addOption("-f", q.formatId)
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
            } else {
                val f = when {
                    safe -> SAFE_FORMAT
                    q.audioId != null -> "${q.formatId}+${q.audioId}"
                    else -> q.formatId
                }
                addOption("-f", f)
                addOption("--merge-output-format", "mp4")
                addOption("--remux-video", "mp4")
                addOption("--postprocessor-args", "Merger+ffmpeg_o:-movflags +faststart")
            }
        }

        val stages = if (safe || q.audioId != null) 2 else 1
        var stage = 0
        var last = 0f
        YoutubeDL.getInstance().execute(req, processId) { p, eta, _ ->
            if (p >= 0f) {
                if (p < last - 30f && stage < stages - 1) stage++
                last = p
                progress = ((stage + p / 100f) / stages).coerceIn(0f, 1f)
            }
            etaText = if (eta > 0) "المتبقي ≈ ${fmtDuration(eta)}" else ""
            pushProgress()
        }

        val okExt = setOf("mp4", "mp3", "m4a", "mkv", "webm")
        val inter = Regex("\\.f\\d+")
        var f = dir.listFiles()
            ?.filter { it.isFile && it.length() > 0 && it.extension.lowercase() in okExt && !inter.containsMatchIn(it.name) }
            ?.maxByOrNull { it.length() }
            ?: throw IllegalStateException("لم يكتمل التحميل أو الدمج. اضغط «تحديث محرك التحميل» ثم أعد المحاولة.")

        // 1) إعادة كتابة الحاوية بصيغة MP4 القياسية لأندرويد (بدون إعادة ترميز)
        var prep = "—"
        if (!q.isAudioOnly && f.extension.lowercase() == "mp4") {
            status = "جارٍ تجهيز الفيديو ليعمل في المعرض..."
            etaText = ""
            val fixed = File(dir, "fixed.mp4")
            val err = Remux.toStandardMp4(f, fixed)
            if (err == null && probe(fixed, false) != null) {
                f.delete()
                f = fixed
                prep = "إعادة الكتابة ✓"
            } else {
                fixed.delete()
                prep = "إعادة الكتابة: لم تتم (${err ?: "ملف غير صالح"})"
            }

            // 2) نقل فهرس الملف (moov) إلى بدايته
            val fast = File(dir, "fast.mp4")
            when (val r = Faststart.toFront(f, fast)) {
                "ok" -> {
                    if (probe(fast, false) != null) {
                        f.delete()
                        f = fast
                        prep += " • الفهرس: نُقل للبداية ✓"
                    } else {
                        fast.delete()
                        prep += " • الفهرس: فشل التحقق"
                    }
                }
                "already" -> prep += " • الفهرس: في البداية ✓"
                else -> {
                    fast.delete()
                    prep += " • الفهرس: تعذر ($r)"
                }
            }
        }

        val p = probe(f, !q.isAudioOnly)
        if (p == null || (!q.isAudioOnly && p.height <= 0)) {
            f.delete()
            throw IllegalStateException("الملف الناتج غير صالح للتشغيل. جرّب جودة أخرى.")
        }
        if (q.isAudioOnly) return Attempt(f, p, null, emptyList(), prep)

        val rep = Compat.check(f.absolutePath)
        val problems = ArrayList(rep.problems)
        if (!p.frameOk) problems += "تعذر استخراج صورة من الفيديو"
        return Attempt(f, p, rep, problems, prep)
    }

    private fun probe(f: File, wantFrame: Boolean): Probe? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.absolutePath)
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return null
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val a = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            var frameOk = true
            if (wantFrame) {
                frameOk = try {
                    val bmp = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    val ok = bmp != null
                    bmp?.recycle()
                    ok
                } catch (e: Exception) {
                    false
                }
            }
            Probe(w, h, d, a, frameOk)
        } catch (e: Exception) {
            null
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    /** الحفظ في قسم الفيديو/الصوت الرسمي ثم فهرسته ليتعرف عليه المعرض ويشغّله. */
    private fun saveToMediaStore(f: File, mime: String, isAudio: Boolean, title: String): Uri? {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "VideoDL_$stamp.${f.extension.lowercase()}"
        val collection = if (isAudio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.TITLE, title)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (isAudio) "Music/VideoDL" else "Movies/VideoDL")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            if (!isAudio) put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
        }
        val r = app.contentResolver
        val uri = r.insert(collection, values) ?: return null
        try {
            r.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                ?: run { r.delete(uri, null, null); return null }
        } catch (e: Exception) {
            r.delete(uri, null, null)
            throw e
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        r.update(uri, values, null, null)

        // فهرسة إجبارية ليحصل المعرض على المدة والأبعاد والصورة المصغّرة
        try {
            r.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val path = c.getString(0)
                    if (!path.isNullOrEmpty()) {
                        MediaScannerConnection.scanFile(app, arrayOf(path), arrayOf(mime), null)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return uri
    }

    // ---------------------------------------------------------------- تحديث المحرك
    fun updateEngine() {
        if (busy || downloading) return
        scope.launch {
            busy = true
            isError = false
            status = "جارٍ تحديث محرك التحميل..."
            try {
                val r = withContext(Dispatchers.IO) {
                    App.initThread.join()
                    YoutubeDL.getInstance().updateYoutubeDL(app)
                }
                status = "اكتمل التحديث: $r"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                isError = true
                status = "فشل التحديث: ${e.message?.take(150)}"
            }
            busy = false
        }
    }

    // ---------------------------------------------------------------- الصورة المصغّرة (محصّنة)
    /** https فقط، بدون عناوين داخلية، بحجم محدود، وتلتقط كل الأخطاء بما فيها نفاد الذاكرة. */
    private fun loadThumb(first: String): ImageBitmap? {
        var u = first
        try {
            repeat(4) {
                if (UrlGuard.check(u) != null) return null
                val c = URL(u).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty("User-Agent", "Mozilla/5.0")
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location")
                    c.disconnect()
                    if (loc == null) return null
                    u = URL(URL(u), loc).toString()
                    return@repeat
                }
                if (code != 200 || c.contentLengthLong > MAX_THUMB_BYTES) {
                    c.disconnect()
                    return null
                }
                val bytes = readLimited(c.inputStream, MAX_THUMB_BYTES)
                c.disconnect()
                return if (bytes == null) null else decodeSmall(bytes)
            }
        } catch (e: Throwable) {
            return null
        }
        return null
    }

    private fun readLimited(s: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        s.use {
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                total += n
                if (total > max) return null
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }

    private fun decodeSmall(b: ByteArray): ImageBitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(b, 0, b.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        if (o.outWidth.toLong() * o.outHeight > 50_000_000L) return null
        var sample = 1
        while (o.outWidth / sample > 1280 || o.outHeight / sample > 1280) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(b, 0, b.size, o2)?.asImageBitmap()
    }

    // ---------------------------------------------------------------- رسائل الأخطاء
    private fun friendly(e: Throwable): String {
        val m = e.message ?: ""
        return when {
            e is OutOfMemoryError -> "الذاكرة غير كافية لإكمال العملية"
            m.contains("Unsupported URL", true) -> "الرابط غير مدعوم"
            m.contains("Unable to resolve host", true) -> "تحقق من اتصال الإنترنت"
            m.contains("Sign in to confirm", true) -> "المنصة طلبت تحققاً. اضغط «تحديث محرك التحميل» ثم أعد المحاولة"
            m.contains("login", true) || m.contains("private", true) || m.contains("cookies", true) ->
                "الفيديو خاص أو يتطلب تسجيل الدخول، لا يمكن تحميله"
            e is IllegalStateException || e is IllegalArgumentException -> m
            else -> "تعذّر التنفيذ: " + (m.lineSequence().firstOrNull { it.contains("ERROR") } ?: m.take(180))
        }
    }
}
