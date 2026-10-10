package com.dormmate.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dormmate.DormMateApp
import com.dormmate.MainActivity
import com.dormmate.R
import com.dormmate.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class DormMateFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            AuthRepository.getInstance().updateFcmToken(currentUser.uid, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        var title: String? = null
        var body: String? = null

        remoteMessage.notification?.let {
            title = it.title
            body = it.body
        }

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            if (data.containsKey("title")) title = data["title"]
            if (data.containsKey("body")) body = data["body"]
        }

        if (title == null) title = getString(R.string.app_name)
        if (body == null) body = ""

        showNotification(title!!, body!!)
    }

    private fun showNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, DormMateApp.FCM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val manager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    companion object {
        private const val TAG = "DormMateFcmService"
        const val KEY_TYPE = "type"
    }
}
