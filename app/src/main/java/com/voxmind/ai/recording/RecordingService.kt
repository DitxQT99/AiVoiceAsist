package com.voxmind.ai.recording

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.voxmind.ai.data.AppDatabase
import com.voxmind.ai.data.Recording
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecordingService : Service() {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (recorder != null) return
        val channelId = "voxmind_recording"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "VoxMind recording", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("VoxMind AI")
            .setContentText("Sedang merekam. Buka aplikasi untuk menghentikan.")
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(301, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(301, notification)

        try {
            val dir = File(getExternalFilesDir(null), "recordings").apply { mkdirs() }
            file = File(dir, "voxmind_${System.currentTimeMillis()}.m4a")
            recorder = newRecorder()
            recorder!!.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file!!.absolutePath)
                prepare()
                start()
            }
        } catch (_: Exception) {
            recorder?.release(); recorder = null
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()

    private fun stopRecording() {
        val active = recorder
        if (active == null) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return }
        val saved = file
        try { active.stop() } catch (_: Exception) { saved?.delete() }
        finally { active.release(); recorder = null; file = null }

        scope.launch {
            if (saved != null && saved.exists() && saved.length() > 1024) {
                val title = "Rekaman " + SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date())
                AppDatabase.get(applicationContext).recordingDao().insert(
                    Recording(title = title, createdAt = System.currentTimeMillis(), audioPath = saved.absolutePath)
                )
            }
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        try { recorder?.let { try { it.stop() } catch (_: Exception) {}; it.release() } } finally {
            recorder = null
            scope.cancel()
            super.onDestroy()
        }
    }
    companion object {
        const val ACTION_START = "com.voxmind.ai.START_RECORDING"
        const val ACTION_STOP = "com.voxmind.ai.STOP_RECORDING"
    }
}
