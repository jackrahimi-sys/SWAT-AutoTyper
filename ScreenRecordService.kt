package com.swat.autotyper

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecordService : Service() {
    companion object {
        const val ACTION_START = "com.swat.autotyper.START_RECORDING"
        const val ACTION_STOP = "com.swat.autotyper.STOP_RECORDING"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "swat_recording"
        private const val NOTIFICATION_ID = 1004
    }

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                createNotificationChannel()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification("در حال ضبط صفحه"),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification("در حال ضبط صفحه"))
                }
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (data == null || resultCode != Activity.RESULT_OK) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                try {
                    startRecording(resultCode, data)
                } catch (e: Exception) {
                    stopRecording()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val dir = getExternalFilesDir("recordings") ?: filesDir
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        outputFile = File(dir, "SWAT_$stamp.mp4")

        recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder?.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(width.coerceAtMost(1080), height.coerceAtMost(1920))
            setVideoFrameRate(24)
            setVideoEncodingBitRate(5_000_000)
            setOutputFile(outputFile!!.absolutePath)
            prepare()
        }
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopRecording()
            }
        }, null)
        virtualDisplay = projection?.createVirtualDisplay(
            "SWAT AutoTyper Screen Recording",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder?.surface, null, null
        )
        recorder?.start()
    }

    private fun stopRecording() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.reset() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        val file = outputFile
        if (file != null && file.exists() && file.length() > 0) {
            val prefs = getSharedPreferences("swat_prefs", Context.MODE_PRIVATE)
            val old = prefs.getString("recordings", "") ?: ""
            prefs.edit().putString("recordings", file.absolutePath + "\n" + old).apply()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "SWAT screen recording", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification {
        val stopIntent = Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("SWAT AutoTyper")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "توقف ضبط", pending).build())
            .build()
    }
}
