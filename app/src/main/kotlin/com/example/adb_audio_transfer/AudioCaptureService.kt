package com.example.adb_audio_transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaProjection
import android.media.MediaProjectionManager
import android.media.projection.MediaProjection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket

class AudioCaptureService : Service() {

    companion object {
        const val TAG = "AudioCaptureService"
        const val NOTIFICATION_CHANNEL_ID = "audio_transfer_channel"
        const val NOTIFICATION_ID = 1
        
        // 音频参数
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE = 1024 * 8
    }

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var isRunning = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra("mode") ?: "sender"
        
        startForeground(NOTIFICATION_ID, createNotification(mode))

        when (mode) {
            "sender" -> {
                val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
                val data = intent?.getParcelableExtra<Intent>("data")
                if (resultCode != -1 && data != null) {
                    startSender(resultCode, data)
                }
            }
            "receiver" -> {
                val serverIp = intent?.getStringExtra("serverIp") ?: "127.0.0.1"
                val serverPort = intent?.getIntExtra("serverPort", 9876) ?: 9876
                startReceiver(serverIp, serverPort)
            }
        }

        return START_STICKY
    }

    private fun startSender(resultCode: Int, data: Intent) {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        serviceScope.launch {
            try {
                // 启动 TCP 服务器，监听所有网卡
                serverSocket = ServerSocket(9876, 50, java.net.InetAddress.getByName("0.0.0.0"))
                Log.i(TAG, "Server started on port 9876 (0.0.0.0)")

                clientSocket = serverSocket?.accept()
                Log.i(TAG, "Client connected: ${clientSocket?.inetAddress}")

                // 配置音频捕获
                val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                
                audioRecord = AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AUDIO_FORMAT)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize * 2)
                    .setAudioPlaybackCaptureConfig(config)
                    .build()

                audioRecord?.startRecording()
                isRunning = true

                val outputStream = DataOutputStream(clientSocket?.getOutputStream())
                val buffer = ByteArray(BUFFER_SIZE)

                // 发送音频参数
                outputStream.writeInt(SAMPLE_RATE)
                outputStream.writeInt(2) // 声道数
                outputStream.flush()

                while (isRunning) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        outputStream.writeInt(read)
                        outputStream.write(buffer, 0, read)
                        outputStream.flush()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Sender error: ${e.message}")
            } finally {
                cleanup()
            }
        }
    }

    private fun startReceiver(serverIp: String, serverPort: Int) {
        serviceScope.launch {
            try {
                clientSocket = Socket(serverIp, serverPort)
                Log.i(TAG, "Connected to server: $serverIp:$serverPort")

                val inputStream = DataInputStream(clientSocket?.getInputStream())

                // 接收音频参数
                val sampleRate = inputStream.readInt()
                val channels = inputStream.readInt()
                
                Log.i(TAG, "Audio params: $sampleRate Hz, $channels channels")

                // 初始化 AudioTrack
                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO,
                    AUDIO_FORMAT
                )

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AUDIO_FORMAT)
                            .setChannelMask(if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()
                isRunning = true

                while (isRunning) {
                    try {
                        val packetSize = inputStream.readInt()
                        if (packetSize > 0 && packetSize <= BUFFER_SIZE) {
                            val buffer = ByteArray(packetSize)
                            inputStream.readFully(buffer)
                            audioTrack?.write(buffer, 0, packetSize)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Receive error: ${e.message}")
                        break
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Receiver error: ${e.message}")
            } finally {
                cleanup()
            }
        }
    }

    private fun cleanup() {
        isRunning = false
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord cleanup error: ${e.message}")
        }

        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack cleanup error: ${e.message}")
        }

        try {
            clientSocket?.close()
            clientSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Client socket cleanup error: ${e.message}")
        }

        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Server socket cleanup error: ${e.message}")
        }

        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Audio Transfer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Audio transfer service"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(mode: String): Notification {
        val modeText = if (mode == "sender") "发送端运行中" else "接收端运行中"
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("ADB Audio Transfer")
            .setContentText(modeText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
        serviceScope.cancel()
    }
}
