package com.heartrunner.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.heartrunner.app.HeartRunnerApp
import com.heartrunner.app.R
import com.heartrunner.app.ble.BleHeartRateManager
import com.heartrunner.app.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * 前台服务，确保蓝牙连接和心率监测在息屏后仍能持续运行
 */
class HeartRateService : Service() {

    private val binder = LocalBinder()
    private var bleManager: BleHeartRateManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    inner class LocalBinder : Binder() {
        fun getService(): HeartRateService = this@HeartRateService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        return START_STICKY
    }

    fun setBleManager(manager: BleHeartRateManager) {
        bleManager = manager
        // 监听心率变化更新通知
        serviceScope.launch {
            manager.heartRate.collectLatest { hr ->
                if (hr > 0) {
                    val notification = buildNotification(hr)
                    val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                    nm.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun buildNotification(heartRate: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (heartRate > 0) "当前心率: $heartRate BPM" else "正在连接心率带..."

        return NotificationCompat.Builder(this, HeartRunnerApp.CHANNEL_ID)
            .setContentTitle("HeartRunner 运行中")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}
