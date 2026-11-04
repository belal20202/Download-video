package com.videodl.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder

object DownloadNotifier {
    private const val CHANNEL = "downloads"
    const val ID_PROGRESS = 1001
    const val ID_DONE = 1002

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "التحميلات", NotificationManager.IMPORTANCE_LOW)
            ch.description = "تقدم تحميل الفيديوهات"
            nm.createNotificationChannel(ch)
        }
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx,
        0,
        Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun buildProgress(ctx: Context, percent: Int, text: String): Notification {
        ensureChannel(ctx)
        val cancel = PendingIntent.getService(
            ctx,
            1,
            Intent(ctx, DownloadService::class.java).setAction(DownloadService.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(ctx, android.R.drawable.ic_menu_close_clear_cancel),
            "إلغاء",
            cancel
        ).build()
        return Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("محمّل الفيديو")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .setContentIntent(openApp(ctx))
            .addAction(action)
            .build()
    }

    fun progress(ctx: Context, percent: Int, text: String) {
        try {
            ctx.getSystemService(NotificationManager::class.java)
                .notify(ID_PROGRESS, buildProgress(ctx, percent, text))
        } catch (_: Exception) {
        }
    }

    fun finished(ctx: Context, ok: Boolean, text: String) {
        try {
            ensureChannel(ctx)
            val n = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                .setContentTitle(if (ok) "اكتمل التحميل ✅" else "تعذّر التحميل")
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(openApp(ctx))
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(ID_DONE, n)
        } catch (_: Exception) {
        }
    }
}

/** خدمة مقدمة: تُبقي العملية حيّة أثناء التحميل حتى لو خرجت من التطبيق. */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            App.vm.cancel()
            return START_NOT_STICKY
        }
        val n = DownloadNotifier.buildProgress(this, 0, "جارٍ التحميل...")
        startForeground(DownloadNotifier.ID_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_CANCEL = "com.videodl.app.CANCEL"

        fun start(ctx: Context) {
            try {
                ctx.startForegroundService(Intent(ctx, DownloadService::class.java))
            } catch (_: Exception) {
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, DownloadService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
