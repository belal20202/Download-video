package com.videodl.app

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainVM(app: Application) : AndroidViewModel(app) {
    var url by mutableStateOf("")
    var title by mutableStateOf<String?>(null)
    var heights by mutableStateOf<List<Int>>(emptyList())
    var selected by mutableStateOf("best") // best | 1080 | 720 ... | audio
    var status by mutableStateOf("الصق رابط الفيديو ثم اضغط «جلب»")
    var progress by mutableStateOf(0f)
    var busy by mutableStateOf(false)
    var downloading by mutableStateOf(false)

    private val processId = "videodl-main"

    fun fetch() {
        val u = url.trim()
        if (u.isEmpty() || busy) return
        viewModelScope.launch {
            busy = true
            title = null
            status = "جارٍ جلب المعلومات..."
            try {
                val info = withContext(Dispatchers.IO) {
                    App.initThread.join()
                    YoutubeDL.getInstance().getInfo(YoutubeDLRequest(u).apply { addOption("--no-playlist") })
                }
                title = info.title
                val hs = info.formats?.mapNotNull { it.height }?.filter { it > 0 }?.distinct()?.sortedDescending()
                heights = if (hs.isNullOrEmpty()) listOf(720, 480, 360) else hs
                selected = "best"
                status = "اختر الجودة ثم اضغط «تحميل»"
            } catch (e: Exception) {
                status = "تعذّر جلب الرابط: ${e.message?.take(200)}"
            }
            busy = false
        }
    }

    fun download() {
        val u = url.trim()
        if (u.isEmpty() || downloading) return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            downloading = true
            progress = 0f
            status = "جارٍ التحميل..."
            try {
                val saved = withContext(Dispatchers.IO) {
                    App.initThread.join()
                    val tmp = File(ctx.cacheDir, "dl").apply { deleteRecursively(); mkdirs() }
                    val req = YoutubeDLRequest(u).apply {
                        addOption("--no-playlist")
                        addOption("-o", "${tmp.absolutePath}/%(title).80s.%(ext)s")
                        when (selected) {
                            "audio" -> {
                                addOption("-f", "ba/b")
                                addOption("-x")
                                addOption("--audio-format", "mp3")
                            }
                            "best" -> {
                                addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/bv*+ba/b")
                                addOption("--merge-output-format", "mp4")
                            }
                            else -> {
                                val h = selected
                                addOption(
                                    "-f",
                                    "bv*[height<=$h][ext=mp4]+ba[ext=m4a]/bv*[height<=$h]+ba/b[height<=$h]/b"
                                )
                                addOption("--merge-output-format", "mp4")
                            }
                        }
                    }
                    YoutubeDL.getInstance().execute(req, processId) { p, _, _ ->
                        if (p >= 0f) progress = p / 100f
                    }
                    val files = tmp.listFiles { f ->
                        f.isFile && !f.name.endsWith(".part") && !f.name.endsWith(".ytdl")
                    }?.toList().orEmpty()
                    var count = 0
                    files.forEach { if (saveToDownloads(ctx, it)) count++; it.delete() }
                    count
                }
                status = if (saved > 0) "تم الحفظ ✅ في: Download/VideoDL" else "لم يتم العثور على ملف ناتج"
                progress = if (saved > 0) 1f else 0f
            } catch (e: Exception) {
                status = "فشل التحميل: ${e.message?.take(200)}"
            }
            downloading = false
        }
    }

    fun cancel() {
        YoutubeDL.getInstance().destroyProcessById(processId)
        status = "تم الإلغاء"
        downloading = false
    }

    fun updateEngine() {
        if (busy || downloading) return
        viewModelScope.launch {
            busy = true
            status = "جارٍ تحديث محرك التحميل..."
            try {
                val r = withContext(Dispatchers.IO) {
                    App.initThread.join()
                    YoutubeDL.getInstance().updateYoutubeDL(ctxApp())
                }
                status = "اكتمل التحديث: $r"
            } catch (e: Exception) {
                status = "فشل التحديث: ${e.message?.take(150)}"
            }
            busy = false
        }
    }

    private fun ctxApp(): Context = getApplication<Application>()

    private fun saveToDownloads(ctx: Context, f: File): Boolean {
        val mime = when (f.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            else -> "application/octet-stream"
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, f.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/VideoDL")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val r = ctx.contentResolver
        val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        r.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        r.update(uri, values, null, null)
        return true
    }
}
