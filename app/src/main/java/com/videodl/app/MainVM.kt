package com.videodl.app

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
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

internal class Probe(val width: Int, val height: Int, val durationMs: Long, val hasAudio: Boolean)
internal class Saved(val uri: Uri, val mime: String, val info: String)

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
                    App.initThread.join()
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
                val saved = withContext(Dispatchers.IO) { runDownload(ctx, u, q) }
                savedUri = saved.uri
                savedMime = saved.mime
                progress = 1f
                status = "تم الحفظ ✅ في: Download/VideoDL\n${saved.info}"
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

    private fun runDownload(ctx: Context, u: String, q: Quality): Saved {
        App.initThread.join()
        val tmp = File(ctx.cacheDir, "dl").apply { deleteRecursively(); mkdirs() }

        val req = YoutubeDLRequest(u).apply {
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--windows-filenames")
            addOption("-o", "${tmp.absolutePath}/%(title).70s.%(ext)s")
            if (q.isAudioOnly) {
                addOption("-f", q.formatId)
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
            } else {
                val f = if (q.audioId != null) "${q.formatId}+${q.audioId}" else q.formatId
                addOption("-f", f)
                addOption("--merge-output-format", "mp4")
                addOption("--remux-video", "mp4")
            }
        }

        val stages = if (q.audioId != null) 2 else 1
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
        val f = tmp.listFiles()
            ?.filter { it.isFile && it.length() > 0 && it.extension.lowercase() in okExt && !inter.containsMatchIn(it.name) }
            ?.maxByOrNull { it.length() }
            ?: throw IllegalStateException("لم يكتمل التحميل أو الدمج. اضغط «تحديث محرك التحميل» ثم أعد المحاولة.")

        // التحقق من أن الملف صالح للتشغيل فعلاً
        val p = probe(f)
        if (p == null || (!q.isAudioOnly && p.height <= 0)) {
            f.delete()
            throw IllegalStateException("الملف الناتج غير صالح للتشغيل. جرّب جودة أخرى.")
        }

        val mime = when (f.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            else -> "application/octet-stream"
        }
        val uri = saveToDownloads(ctx, f, mime)
            ?: throw IllegalStateException("تعذر حفظ الملف في مجلد التنزيلات")
        f.delete()
        tmp.deleteRecursively()

        val dur = fmtDuration(p.durationMs / 1000)
        val info = if (q.isAudioOnly) {
            "ملف صوتي • المدة $dur"
        } else {
            "الدقة الفعلية ${p.width}×${p.height} • المدة $dur" +
                if (!p.hasAudio) "\n⚠ هذا الفيديو بدون صوت" else ""
        }
        return Saved(uri, mime, info)
    }

    private fun probe(f: File): Probe? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.absolutePath)
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return null
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val a = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            Probe(w, h, d, a)
        } catch (e: Exception) {
            null
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun saveToDownloads(ctx: Context, f: File, mime: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, f.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/VideoDL")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val r = ctx.contentResolver
        val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        r.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        r.update(uri, values, null, null)
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
