package com.videodl.app

import android.app.Application
import android.content.ContentValues
import android.content.Context
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

internal class Saved(val uri: Uri, val mime: String, val info: String)

// نسخة أكثر توافقاً: H.264 + AAC حتى 720p و30 إطار
private const val SAFE_FORMAT =
    "bv*[vcodec^=avc1][height<=?720][fps<=?30]+ba[acodec^=mp4a]/" +
        "b[vcodec^=avc1][acodec^=mp4a][height<=?720]/" +
        "b[ext=mp4][height<=?720]/b[ext=mp4]"

class MainVM(app: Application) : AndroidViewModel(app) {
    var url by mutableStateOf("")
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

    private val processId = "videodl-main"

    @Volatile
    private var cancelled = false

    private fun extractUrl(text: String): String? =
        Regex("https?://\\S+").find(text.trim())?.value

    private fun awaitEngine() {
        App.initThread.join()
        App.initError?.let { throw IllegalStateException("تعذر تشغيل محرك التحميل: $it") }
    }

    // ---------------------------------------------------------------- التعرف على الفيديو
    fun fetch() {
        val u = extractUrl(url)
        if (u == null) {
            isError = true
            status = "الصق رابطاً صحيحاً يبدأ بـ https://"
            return
        }
        if (busy || downloading) return
        viewModelScope.launch {
            busy = true
            isError = false
            meta = null
            thumb = null
            savedUri = null
            progress = 0f
            dua = ""
            status = "جارٍ التعرف على الفيديو..."
            try {
                val parsed = withContext(Dispatchers.IO) {
                    awaitEngine()
                    val req = YoutubeDLRequest(u).apply {
                        addOption("--dump-single-json")
                        addOption("--no-playlist")
                        addOption("--no-warnings")
                    }
                    VideoParser.parse(YoutubeDL.getInstance().execute(req).out)
                }
                meta = parsed
                selectedIndex = parsed.qualities
                    .indexOfFirst { !it.isAudioOnly && it.res in 1..1080 }
                    .coerceAtLeast(0)
                status = "تم التعرف على الفيديو. اختر الجودة ثم اضغط تحميل"
                if (parsed.thumbnail.isNotEmpty()) {
                    launch(Dispatchers.IO) { thumb = loadThumb(parsed.thumbnail) }
                }
            } catch (e: Exception) {
                isError = true
                status = friendly(e)
            }
            busy = false
        }
    }

    // ---------------------------------------------------------------- التحميل
    fun download() {
        val m = meta ?: return
        val q = m.qualities.getOrNull(selectedIndex) ?: return
        val u = extractUrl(url) ?: return
        if (downloading || busy) return
        val ctx = getApplication<Application>()
        cancelled = false
        viewModelScope.launch {
            downloading = true
            isError = false
            progress = 0f
            etaText = ""
            savedUri = null
            dua = Duas.next()
            status = "جارٍ التحميل بجودة ${q.label}..."
            try {
                val saved = withContext(Dispatchers.IO) { runDownload(ctx, u, q, m.title) }
                savedUri = saved.uri
                savedMime = saved.mime
                progress = 1f
                val folder = if (q.isAudioOnly) "Music/VideoDL" else "Movies/VideoDL"
                status = "تم الحفظ ✅ في: $folder (يظهر في المعرض)\n${saved.info}"
            } catch (e: Exception) {
                progress = 0f
                if (cancelled) {
                    status = "تم إلغاء التحميل"
                } else {
                    isError = true
                    status = friendly(e)
                }
            }
            downloading = false
            etaText = ""
        }
    }

    fun cancel() {
        cancelled = true
        try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (_: Exception) {
        }
    }

    private fun runDownload(ctx: Context, u: String, q: Quality, title: String): Saved {
        awaitEngine()
        val dirA = File(ctx.cacheDir, "dl")
        val dirB = File(ctx.cacheDir, "dl_safe")

        var chosen = attempt(u, q, dirA, false)
        var fellBack = false

        // إذا ظهرت مشكلة توافق، ننزّل تلقائياً نسخة أكثر توافقاً
        if (chosen.problems.isNotEmpty() && !q.isAudioOnly) {
            status = "⚠ ${chosen.problems.first()}\nجارٍ تنزيل نسخة أكثر توافقاً مع المعرض..."
            progress = 0f
            etaText = ""
            val b: Attempt? = try {
                attempt(u, q, dirB, true)
            } catch (e: Exception) {
                if (cancelled) throw e
                null
            }
            if (b != null && b.problems.size < chosen.problems.size) {
                chosen.file.delete()
                chosen = b
                fellBack = true
            }
        }

        val f = chosen.file
        val p = chosen.probe
        val mime = when (f.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            else -> "application/octet-stream"
        }
        val isAudio = mime.startsWith("audio")
        val uri = saveToMediaStore(ctx, f, mime, isAudio, title)
            ?: throw IllegalStateException("تعذر حفظ الملف على الجهاز")
        dirA.deleteRecursively()
        dirB.deleteRecursively()

        val dur = fmtDuration(p.durationMs / 1000)
        val sb = StringBuilder()
        if (q.isAudioOnly) {
            sb.append("ملف صوتي • المدة ").append(dur)
        } else {
            sb.append("الدقة الفعلية ${p.width}×${p.height} • المدة ").append(dur)
            chosen.report?.let { if (it.summary.isNotEmpty()) sb.append("\n").append(it.summary) }
            sb.append("\nتجهيز الملف: ").append(chosen.prep)
            if (fellBack) sb.append("\n↺ تم تنزيل نسخة أكثر توافقاً تلقائياً (H.264 حتى 720p)")
            if (chosen.problems.isNotEmpty()) {
                sb.append("\n⚠ ").append(chosen.problems.joinToString("، "))
                sb.append("\nاستخدم «تشغيل الملف» لمشاهدته")
            }
            if (!p.hasAudio) sb.append("\n⚠ هذا الفيديو بدون صوت")
        }
        return Saved(uri, mime, sb.toString())
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
        }

        val okExt = setOf("mp4", "mp3", "m4a", "mkv", "webm")
        val inter = Regex("\\.f\\d+")
        var f = dir.listFiles()
            ?.filter { it.isFile && it.length() > 0 && it.extension.lowercase() in okExt && !inter.containsMatchIn(it.name) }
            ?.maxByOrNull { it.length() }
            ?: throw IllegalStateException("لم يكتمل التحميل أو الدمج. اضغط «تحديث محرك التحميل» ثم أعد المحاولة.")

        // إعادة كتابة الحاوية بصيغة MP4 القياسية لأندرويد (بدون إعادة ترميز)
        var prep = "—"
        if (!q.isAudioOnly && f.extension.lowercase() == "mp4") {
            status = "جارٍ تجهيز الفيديو ليعمل في المعرض..."
            etaText = ""
            val fixed = File(dir, "fixed.mp4")
            val err = Remux.toStandardMp4(f, fixed)
            if (err == null && probe(fixed, false) != null) {
                f.delete()
                f = fixed
                prep = "تم ✓"
            } else {
                fixed.delete()
                prep = "لم يتم (${err ?: "ملف غير صالح"})"
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
    private fun saveToMediaStore(ctx: Context, f: File, mime: String, isAudio: Boolean, title: String): Uri? {
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
        val r = ctx.contentResolver
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
                        MediaScannerConnection.scanFile(ctx, arrayOf(path), arrayOf(mime), null)
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
        viewModelScope.launch {
            busy = true
            isError = false
            status = "جارٍ تحديث محرك التحميل..."
            try {
                val r = withContext(Dispatchers.IO) {
                    App.initThread.join()
                    YoutubeDL.getInstance().updateYoutubeDL(getApplication<Application>())
                }
                status = "اكتمل التحديث: $r"
            } catch (e: Exception) {
                isError = true
                status = "فشل التحديث: ${e.message?.take(150)}"
            }
            busy = false
        }
    }

    // ---------------------------------------------------------------- مساعدات
    private fun loadThumb(u: String): ImageBitmap? = try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        c.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    } catch (e: Exception) {
        null
    }

    private fun friendly(e: Exception): String {
        val m = e.message ?: ""
        return when {
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
