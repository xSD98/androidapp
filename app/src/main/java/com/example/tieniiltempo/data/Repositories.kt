package com.example.tieniiltempo.data

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

object Repo {

    private val auth get() = FirebaseAuth.getInstance()
    private val db   get() = FirebaseFirestore.getInstance()
    private val storage get() = FirebaseStorage.getInstance()

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

    suspend fun caregiverUsers(caregiverId: String): List<AppUser> {
        val q = db.collection("users")
            .whereEqualTo("caregiverId", caregiverId)
            .get().await()
        return q.documents.mapNotNull { it.toObject(AppUser::class.java)?.copy(uid = it.id) }
    }

    suspend fun searchUnassignedUsers(search: String = ""): List<AppUser> {
        val q = db.collection("users")
            .whereEqualTo("role", "user")
            .orderBy("displayName", Query.Direction.ASCENDING)
            .get().await()
        val all = q.documents.mapNotNull { it.toObject(AppUser::class.java)?.copy(uid = it.id) }
        return all.filter {
            it.caregiverId.isNullOrBlank() &&
                    (search.isBlank() || it.displayName.contains(search, ignoreCase = true))
        }
    }

    suspend fun assignUserToCaregiver(userId: String, caregiverId: String) {
        db.collection("users").document(userId).update("caregiverId", caregiverId).await()
    }

    // -------------------- ACTIVITIES --------------------

    suspend fun createActivity(a: ActivityTT, subtasks: List<Subtask>): String {
        val doc = db.collection("activities").document()
        val expectedTotal = subtasks.sumOf { it.expectedMinutes }

        // chi sta creando probabilmente è il caregiver loggato
        val currentCg = FirebaseAuth.getInstance().currentUser?.uid

        val a2 = a.copy(
            id = doc.id,
            caregiverId = a.caregiverId ?: currentCg, // <-- mai null in scrittura
            expectedMinutes = expectedTotal,
            status = "PLANNED",
            createdAt = Timestamp.now()
        )

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
        db.collection("activities").document(activityId)
            .collection("subtasks").document(subtaskId)
            .update("startedAt", FieldValue.serverTimestamp())
            .await()
        db.collection("activities").document(activityId)
            .update(mapOf("status" to "RUNNING", "startedAt" to FieldValue.serverTimestamp()))
            .await()
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
            .update("completedAt", FieldValue.serverTimestamp()).await()

        // alert se sforo
        if (actualMin > expectedMin) {
            val doc = db.collection("alerts").document()
            val a = Alert(
                id = doc.id,
                caregiverId = caregiverId, userId = userId,
                activityId = activityId, subtaskId = subtaskId,
                minutesExpected = expectedMin, minutesActual = actualMin
            )
            doc.set(a).await()
        }

        // chiusura attività se tutte complete
        val subs = subtasks(activityId)
        if (subs.all { it.completedAt != null }) {
            db.collection("activities").document(activityId)
                .update(mapOf("status" to "DONE", "completedAt" to FieldValue.serverTimestamp()))
                .await()

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

    // -------------------- SUBTASK: LOCATION --------------------

    suspend fun updateSubtaskLocation(activityId: String, subtaskId: String, lat: Double, lng: Double) {
        val geo = com.google.firebase.firestore.GeoPoint(lat, lng)
        db.collection("activities").document(activityId)
            .collection("subtasks").document(subtaskId)
            .update("location", geo)
            .await()
    }

    // -------------------- SUBTASK COMMENTS --------------------

    suspend fun addSubtaskComment(
        activityId: String,
        subtaskId: String,
        authorId: String,
        authorRole: String, // "user" | "caregiver"
        text: String,
        imageUri: Uri? // opzionale, verrà caricato su Storage se presente
    ) {
        val col = db.collection("activities").document(activityId)
            .collection("subtasks").document(subtaskId)
            .collection("comments")

        val commentDoc = col.document()
        var imageUrl: String? = null

        if (imageUri != null) {
            val ref = storage.reference
                .child("subtask_comments/$activityId/$subtaskId/${commentDoc.id}.jpg")
            ref.putFile(imageUri).await()
            imageUrl = ref.downloadUrl.await().toString()
        }

        val c = SubtaskComment(
            id = commentDoc.id,
            authorId = authorId,
            authorRole = authorRole,
            text = text,
            imageUrl = imageUrl,
            createdAt = Timestamp.now()
        )
        commentDoc.set(c).await()
    }

    suspend fun listSubtaskComments(activityId: String, subtaskId: String): List<SubtaskComment> {
        val q = db.collection("activities").document(activityId)
            .collection("subtasks").document(subtaskId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get().await()
        return q.documents.mapNotNull { it.toObject(SubtaskComment::class.java)?.copy(id = it.id) }
    }

    // -------------------- ACTIVITY REVIEW --------------------

    suspend fun saveActivityReview(activityId: String, rating: Int, comment: String, caregiverId: String) {
        val review = ActivityReview(
            rating = rating.coerceIn(1,5),
            comment = comment,
            caregiverId = caregiverId,
            createdAt = Timestamp.now()
        )
        db.collection("activities").document(activityId)
            .set(mapOf("review" to review), SetOptions.merge())
            .await()
    }

    // -------------------- CHAT (già usate altrove) --------------------

    fun chatId(a: String, b: String) = listOf(a, b).sorted().joinToString("_")

    suspend fun sendMessage(fromId: String, toId: String, text: String) {
        val cId = chatId(fromId, toId)
        val doc = db.collection("chats").document(cId)
            .collection("messages").document()
        val msg = ChatMessage(id = doc.id, chatId = cId, fromId = fromId, toId = toId, text = text)
        doc.set(msg).await()
        db.collection("chats").document(cId)
            .set(mapOf("lastAt" to FieldValue.serverTimestamp()), SetOptions.merge())
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
    // --- SUBTASKS: creazione e ricalcolo minuti previsti ---
    suspend fun addSubtask(activityId: String, sub: Subtask): String {
        val actRef = db.collection("activities").document(activityId)
        val subRef = actRef.collection("subtasks").document()
        val toSave = sub.copy(id = subRef.id)

        // 1) salva la sotto-attività
        subRef.set(toSave).await()

        // 2) ricalcola i minuti previsti totali leggendo tutte le sotto-attività
        val snap = actRef.collection("subtasks").get().await()
        val totalExpected = snap.documents
            .mapNotNull { it.toObject(Subtask::class.java)?.expectedMinutes }
            .sum()

        // 3) aggiorna il totale sull'attività
        actRef.update("expectedMinutes", totalExpected).await()

        return subRef.id
    }
}