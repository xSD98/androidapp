package com.example.tieniiltempo.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Repository unico: tutte le letture/scritture su Firestore.
 * NB: 'db' è un getter (NON campo statico) per evitare il warning di memory leak.
 */
object Repo {

    private val auth get() = FirebaseAuth.getInstance()
    private val db   get() = FirebaseFirestore.getInstance()

    // -------------------- USERS --------------------

    suspend fun currentUser(): AppUser? {
        val uid = auth.currentUser?.uid ?: return null
        val snap = db.collection("users").document(uid).get().await()
        return snap.toObject(AppUser::class.java)?.copy(uid = uid)
    }

    suspend fun upsertUser(u: AppUser) {
        val id = if (u.uid.isNotBlank()) u.uid else (auth.currentUser?.uid ?: db.collection("users").document().id)
        db.collection("users").document(id).set(u.copy(uid = id)).await()
    }

    /** Utenti assegnati ad un caregiver */
    // UTENTI ASSEGNATI A UN CAREGIVER (senza indice, filtro lato client)
    suspend fun caregiverUsers(caregiverId: String): List<AppUser> {
        val q = db.collection("users")
            .whereEqualTo("caregiverId", caregiverId)
            .get().await()

        return q.documents
            .mapNotNull { it.toObject(AppUser::class.java)?.copy(uid = it.id) }
            .filter { it.role.equals("user", ignoreCase = true) }
    }


    /** Ricerca (client-side) utenti non assegnati e con role=user */
    suspend fun searchUnassignedUsers(search: String = ""): List<AppUser> {
        val q = db.collection("users")
            .whereEqualTo("role", "user")
            .orderBy("displayName", Query.Direction.ASCENDING)
            .get().await()
        val all = q.documents.mapNotNull { it.toObject(AppUser::class.java)?.copy(uid = it.id) }
        return all.filter { it.caregiverId.isNullOrBlank() && it.displayName.contains(search, true) }
    }

    /** Assegna UTENTE -> CAREGIVER in transazione (garantisce unicità) */
    // ASSEGNA UTENTE: solo se role=user e non è già assegnato (transazione)
    suspend fun assignUserToCaregiver(userId: String, caregiverId: String) {
        db.runTransaction { tr ->
            val ref = db.collection("users").document(userId)
            val u = tr.get(ref).toObject(AppUser::class.java)
                ?: throw IllegalStateException("Utente non trovato")

            if (!u.role.equals("user", true))
                throw IllegalStateException("L'account selezionato non è un utente")

            if (!u.caregiverId.isNullOrBlank())
                throw IllegalStateException("Utente già assegnato")

            tr.update(ref, "caregiverId", caregiverId)
        }.await()
    }

    // DISSOCIA: solo se appartiene a questo caregiver (transazione)
    suspend fun unassignUserFromCaregiver(userId: String, caregiverId: String) {
        db.runTransaction { tr ->
            val ref = db.collection("users").document(userId)
            val u = tr.get(ref).toObject(AppUser::class.java)
                ?: throw IllegalStateException("Utente non trovato")

            if (u.caregiverId != caregiverId)
                throw IllegalStateException("Non appartiene a te")

            tr.update(ref, "caregiverId", null)
        }.await()
    }



    // -------------------- ACTIVITIES --------------------

    suspend fun createActivity(a: ActivityTT, subtasks: List<Subtask>): String {
        val doc = db.collection("activities").document()
        val expectedTotal = subtasks.sumOf { it.expectedMinutes }
        val a2 = a.copy(id = doc.id, expectedMinutes = expectedTotal, status = "PLANNED", createdAt = Timestamp.now())

        db.runTransaction { tr ->
            tr.set(doc, a2)
            subtasks.forEach { st ->
                val stDoc = doc.collection("subtasks").document()
                tr.set(stDoc, st.copy(id = stDoc.id))
            }
        }.await()
        return doc.id
    }

    suspend fun userActivities(userId: String): List<ActivityTT> {
        val q = db.collection("activities")
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()
        return q.documents.mapNotNull { it.toObject(ActivityTT::class.java)?.copy(id = it.id) }
    }

    suspend fun activity(activityId: String): ActivityTT? {
        val d = db.collection("activities").document(activityId).get().await()
        return d.toObject(ActivityTT::class.java)?.copy(id = d.id)
    }

    suspend fun subtasks(activityId: String): List<Subtask> {
        val q = db.collection("activities").document(activityId)
            .collection("subtasks")
            .orderBy("stage", Query.Direction.ASCENDING)
            .get().await()
        return q.documents.mapNotNull { it.toObject(Subtask::class.java)?.copy(id = it.id) }
    }

    suspend fun markSubtaskStarted(activityId: String, subtaskId: String) {
        val now = Timestamp.now()
        db.collection("activities").document(activityId)
            .collection("subtasks").document(subtaskId)
            .update("startedAt", now).await()

        db.collection("activities").document(activityId)
            .update(mapOf("status" to "RUNNING", "startedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun markSubtaskCompleted(
        activityId: String,
        subtaskId: String,
        expectedMin: Int,
        actualMin: Int,
        caregiverId: String,
        userId: String
    ) {
        db.collection("activities").document(activityId)
            .collection("subtasks").document(subtaskId)
            .update("completedAt", Timestamp.now()).await()

        // Sforamento → alert
        if (actualMin > expectedMin) {
            val doc = db.collection("alerts").document()
            val a = Alert(
                id = doc.id,
                caregiverId = caregiverId,
                userId = userId,
                activityId = activityId,
                subtaskId = subtaskId,
                minutesExpected = expectedMin,
                minutesActual = actualMin,
                createdAt = Timestamp.now()
            )
            doc.set(a).await()
        }

        // Se tutte le sotto-attività sono complete, chiudi l'attività
        val subs = subtasks(activityId)
        if (subs.all { it.completedAt != null }) {
            db.collection("activities").document(activityId)
                .update(mapOf("status" to "DONE", "completedAt" to FieldValue.serverTimestamp())).await()

            // Gamification minima
            val onTimeForAll = subs.all { st ->
                val actual = minutesBetween(st.startedAt, st.completedAt)
                actual != null && actual <= st.expectedMinutes
            }
            updateGamificationOnActivityComplete(userId, onTimeForAll)
        }
    }

    private fun minutesBetween(start: Timestamp?, end: Timestamp?): Int? {
        if (start == null || end == null) return null
        val ms = end.toDate().time - start.toDate().time
        return (ms / 60000.0).toInt()
    }

    // -------------------- CHAT --------------------

    fun chatId(a: String, b: String) = listOf(a, b).sorted().joinToString("_")

    suspend fun sendMessage(fromId: String, toId: String, text: String) {
        val cId = chatId(fromId, toId)
        val doc = db.collection("chats").document(cId)
            .collection("messages").document()
        val msg = ChatMessage(
            id = doc.id,
            chatId = cId,
            fromId = fromId,
            toId = toId,
            text = text,
            createdAt = Timestamp.now()
        )
        doc.set(msg).await()

        // opzionale: aggiornare ultimi messaggi/lastAt su chats root per elenco thread
        db.collection("chats").document(cId)
            .set(mapOf("lastAt" to FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    suspend fun loadMessages(withId: String): List<ChatMessage> {
        val me = auth.currentUser?.uid ?: return emptyList()
        val cId = chatId(me, withId)
        val q = db.collection("chats").document(cId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .get().await()
        // Restituisco dal più vecchio al più nuovo (comodo per la lista normale)
        return q.documents.mapNotNull { it.toObject(ChatMessage::class.java)?.copy(id = it.id) }.reversed()
    }

    // -------------------- GAMIFICATION --------------------

    private suspend fun updateGamificationOnActivityComplete(userId: String, allOnTime: Boolean) {
        val ref = db.collection("gamification").document(userId)
        db.runTransaction { tr ->
            val cur = tr.get(ref).toObject(Gamification::class.java) ?: Gamification(userId)
            val newOnTime = cur.onTimeCount + if (allOnTime) 1 else 0
            val newStreak = cur.streakDays + 1
            val newBadges = cur.badges.toMutableSet()

            if (newOnTime >= 3) newBadges.add("Puntuale ×3")
            if (newOnTime >= 10) newBadges.add("Pro delle routine")
            if (newStreak >= 7) newBadges.add("Maratoneta 7")

            tr.set(ref, cur.copy(onTimeCount = newOnTime, streakDays = newStreak, badges = newBadges.toList()))
        }.await()
    }

}
