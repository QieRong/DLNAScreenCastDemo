package com.qierong.dlnascreencastdemo.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.qierong.dlnascreencastdemo.MainActivity

class ScreenCaptureService : Service() {
    private val stateStore = CaptureStateStore.global
    private lateinit var configProvider: AndroidCaptureConfigProvider
    private lateinit var projectionManager: MediaProjectionManager
    private var captureManager: ScreenCaptureManager? = null

    override fun onCreate() {
        super.onCreate()
        configProvider = AndroidCaptureConfigProvider(applicationContext)
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture(intent)
            ACTION_STOP -> requestStop()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        captureManager?.refreshConfig()
    }

    override fun onDestroy() {
        captureManager?.stop()
        captureManager = null
        if (stateStore.state.value.hasActiveSession) stateStore.markIdle()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCapture(intent: Intent) {
        if (captureManager != null) {
            Log.w(TAG, "忽略重复的开始采集请求")
            return
        }
        startProjectionForeground()
        stateStore.markStarting()
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = intent.readResultData()
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            failAndStop("缺少有效的系统录屏授权")
            return
        }

        try {
            val mediaProjection = requireNotNull(
                projectionManager.getMediaProjection(resultCode, resultData),
            ) {
                "系统未返回 MediaProjection"
            }
            val manager = ScreenCaptureManager(
                mediaProjection = mediaProjection,
                configProvider = configProvider,
                playbackAudioPermissionGranted = intent.getBooleanExtra(
                    EXTRA_PLAYBACK_AUDIO_PERMISSION_GRANTED,
                    false,
                ),
                onSessionChanged = stateStore::markCapturing,
                onReconfiguring = stateStore::markReconfiguring,
                onError = ::failAndStop,
                onReleased = ::finishStop,
            )
            captureManager = manager
            manager.start(configProvider.current())
        } catch (exception: Exception) {
            Log.e(TAG, "启动屏幕采集失败", exception)
            captureManager?.stop()
            captureManager = null
            failAndStop(exception.message ?: "启动屏幕采集失败")
        }
    }

    private fun requestStop() {
        stateStore.markStopping()
        val manager = captureManager
        if (manager == null) {
            finishStop()
        } else {
            manager.stop()
        }
    }

    private fun failAndStop(detail: String) {
        stateStore.markError(detail)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finishStop() {
        captureManager = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stateStore.markIdle()
        stopSelf()
    }

    private fun startProjectionForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "屏幕采集",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP)
        val openIntent = Intent(this, MainActivity::class.java)
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("DLNA 手机投屏 Demo")
            .setContentText("正在采集屏幕画面")
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    openIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    "停止采集",
                    PendingIntent.getService(
                        this,
                        0,
                        stopIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).build(),
            )
            .setOngoing(true)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun Intent.readResultData(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            getParcelableExtra(EXTRA_RESULT_DATA)
        }

    companion object {
        private const val TAG = "ScreenCapture"
        private const val ACTION_START =
            "com.qierong.dlnascreencastdemo.capture.action.START"
        private const val ACTION_STOP =
            "com.qierong.dlnascreencastdemo.capture.action.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val EXTRA_PLAYBACK_AUDIO_PERMISSION_GRANTED =
            "playback_audio_permission_granted"
        private const val NOTIFICATION_CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 1001

        fun start(
            context: Context,
            resultCode: Int,
            resultData: Intent,
            playbackAudioPermissionGranted: Boolean,
        ) {
            val intent = Intent(context, ScreenCaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
                .putExtra(
                    EXTRA_PLAYBACK_AUDIO_PERMISSION_GRANTED,
                    playbackAudioPermissionGranted,
                )
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
