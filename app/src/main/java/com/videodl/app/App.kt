package com.videodl.app

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // حالة التطبيق تعيش مع العملية، فتبقى سليمة عند تدوير الشاشة أو الخروج والعودة أثناء التحميل
        vm = MainVM(this)
        initThread = Thread {
            try {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
            } catch (e: Throwable) {
                initError = e.message ?: e.javaClass.simpleName
                Log.e("VideoDL", "init failed", e)
            }
        }.also { it.start() }
    }

    companion object {
        lateinit var initThread: Thread
        lateinit var vm: MainVM

        @Volatile
        var initError: String? = null
    }
}
