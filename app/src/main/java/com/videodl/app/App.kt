package com.videodl.app

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        initThread = Thread {
            try {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
            } catch (e: Exception) {
                Log.e("VideoDL", "init failed", e)
            }
        }.also { it.start() }
    }

    companion object {
        lateinit var initThread: Thread
    }
}
