package com.heartrunner.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class HeartRunnerApp : Application() {

    companion object {
        const val CHANNEL_ID = "heart_rate_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "心率监测",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "蓝牙心率带连接与心率监测"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
