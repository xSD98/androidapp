package com.example.tieniiltempo.core

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

object RealtimeWatchers {
    private var chatReg: ListenerRegistration? = null

    // id della conversazione aperta (otherId: l'altro utente)
    @Volatile private var openChatWith: String? = null
    fun setOpenChat(otherId: String?) { openChatWith = otherId }

    fun startAll(ctx: Context, uid: String) {
        stopAll()
        startChatWatcher(ctx, uid)
    }

    fun stopAll() {
        chatReg?.remove(); chatReg = null
        openChatWith = null
    }

    // ---- Chat watcher ----

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("watchers", Context.MODE_PRIVATE)

    private fun getLastChatTs(ctx: Context, uid: String): Long =
        prefs(ctx).getLong("last_chat_ts_$uid", 0L)

    private fun setLastChatTs(ctx: Context, uid: String, ts: Long) {
        prefs(ctx).edit { putLong("last_chat_ts_$uid", ts) }
    }

    private fun canNotify(ctx: Context): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    private fun startChatWatcher(ctx: Context, uid: String) {
        val db = FirebaseFirestore.getInstance()
        var lastSeen = getLastChatTs(ctx, uid)
        var maxSeen = lastSeen
        var firstSnapshot = (lastSeen == 0L)

        chatReg = db.collectionGroup("messages")
            .whereEqualTo("toId", uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snaps, err ->
                if (err != null || snaps == null) return@addSnapshotListener

                // Se è il primissimo attach (nessun baseline memorizzato), setto baseline e non notifico lo storico
                if (firstSnapshot) {
                    val now = System.currentTimeMillis()
                    maxSeen = now
                    setLastChatTs(ctx, uid, now)
                    lastSeen = now
                    firstSnapshot = false
                    return@addSnapshotListener
                }

                for (dc in snaps.documentChanges) {
                    if (dc.type != DocumentChange.Type.ADDED) continue

                    val d       = dc.document
                    val fromId  = d.getString("fromId") ?: continue
                    val toId    = d.getString("toId")   ?: continue
                    val text    = d.getString("text")   ?: ""
                    val ts      = (d.getTimestamp("createdAt") ?: Timestamp(0,0)).toDate().time

                    // scarta messaggi non destinati, inviati da me o già visti
                    if (toId != uid) continue
                    if (fromId == uid) continue
                    if (ts <= lastSeen) continue

                    // se la chat con 'fromId' è aperta → non notifico
                    if (openChatWith == fromId) {
                        if (ts > maxSeen) maxSeen = ts
                        continue
                    }

                    if (canNotify(ctx)) {
                        try {
                            Notifier.notify(
                                ctx,
                                title = "Nuovo messaggio",
                                body  = if (text.isBlank()) "Hai ricevuto un messaggio" else text
                            )
                        } catch (_: SecurityException) {
                            // permesso non concesso: ignoro
                        }
                    }

                    if (ts > maxSeen) maxSeen = ts
                }

                if (maxSeen > lastSeen) {
                    setLastChatTs(ctx, uid, maxSeen)
                    lastSeen = maxSeen
                }
            }
    }
}