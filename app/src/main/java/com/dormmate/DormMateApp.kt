package com.dormmate

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class DormMateApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FCM_CHANNEL_ID,
                FCM_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Maintenance, billing, and announcement alerts"
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val FCM_CHANNEL_ID = "dormmate_default"
        const val FCM_CHANNEL_NAME = "DormMate Notifications"
    }
}
