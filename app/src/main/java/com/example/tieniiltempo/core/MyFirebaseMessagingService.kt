package com.example.tieniiltempo.core

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // Puoi salvarlo su Firestore se vuoi invii FCM mirati
    }
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: "Nuovo messaggio"
        val body = message.notification?.body ?: "Hai ricevuto una notifica"
        Notifier.notify(this, title, body)
    }
}
