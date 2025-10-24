package com.example.tieniiltempo.data

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.GeoPoint

object Repo {

    private val auth get() = FirebaseAuth.getInstance()
    private val db   get() = FirebaseFirestore.getInstance()
    private val storage get() = Firebase.storage

    // ---------- Costanti gamification ----------
    private const val GOLD_POINTS = 100
    private const val SILVER_POINTS = 50
    private const val BRONZE_POINTS = 25
    private const val PLATINUM_POINTS = 250
    private const val MAX_LEVEL = 100
    private fun nextLevelCost(level: Int): Int = (level.coerceAtLeast(1)) * 100

    // -------------------- Helper robusto per AppUser --------------------

    /**
     * Converte un documento Firestore in AppUser **sanificando i campi**:
     * - Strings mai null ("" se assenti)
     * - role default "user"
     * - caregiverId sempre stringa ("" se assente)
     *
     * Evita l'uso di `toObject(...).copy(...)` che può lanciare NPE se
     * Firestore ha scritto null su campi Kotlin non-null.
     */
    private fun docToAppUser(doc: DocumentSnapshot): AppUser {
        val d = doc.data ?: emptyMap<String, Any?>()
        val email        = (d["email"] as? String) ?: ""
        val displayName  = (d["displayName"] as? String) ?: ""
        val role         = (d["role"] as? String) ?: "user"
        val caregiverId  = (d["caregiverId"] as? String) ?: ""
        // Se il tuo AppUser ha altri campi con default, il costruttore li userà.
        return AppUser(
            uid = doc.id,
            email = email,
            displayName = displayName,
            role = role,
            caregiverId = caregiverId
        )
    }

    // -------------------- USERS --------------------

    suspend fun currentUser(): AppUser? {
        val uid = auth.currentUser?.uid ?: return null
        val snap = db.collection("users").document(uid).get().await()
        if (!snap.exists()) return null
        return docToAppUser(snap)
    }

    suspend fun caregiverUsers(caregiverId: String): List<AppUser> {
        val q = db.collection("users")
            .whereEqualTo("caregiverId", caregiverId)
            .get().await()
        return q.documents.map { docToAppUser(it) }
    }

    suspend fun searchUnassignedUsers(search: String = ""): List<AppUser> {
        val q = db.collection("users")
            .whereEqualTo("role", "user")
            .orderBy("displayName", Query.Direction.ASCENDING)
            .get().await()
        val all = q.documents.map { docToAppUser(it) }
        return all.filter {
            it.caregiverId.isBlank() &&
                    (search.isBlank()
                            || it.displayName.contains(search, ignoreCase = true)
                            || it.email.contains(search, ignoreCase = true))
        }
    }

    /**
     * Assegna l'utente a un caregiver. Passa "" per disassociare.
     * (Scriviamo SEMPRE stringa, mai null)
     */
    suspend fun assignUserToCaregiver(userId: String, caregiverId: String) {
        db.collection("users").document(userId)
            .set(mapOf("caregiverId" to caregiverId), SetOptions.merge())
            .await()
    }

    // -------------------- ACTIVITIES --------------------

    suspend fun createActivity(a: ActivityTT, subtasks: List<Subtask>): String {
        val doc = db.collection("activities").document()
        val expectedTotal = subtasks.sumOf { it.expectedMinutes }

        val currentCg = FirebaseAuth.getInstance().currentUser?.uid

        val a2 = a.copy(
            id = doc.id,
            caregiverId = a.caregiverId ?: currentCg,
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
        expectedMin: Int,    // compat
        actualMin: Int,      // compat
        caregiverId: String,
        userId: String
    ) {
        val actRef = db.collection("activities").document(activityId)
        val subRef = actRef.collection("subtasks").document(subtaskId)

        // 1) chiude con server time
        subRef.update("completedAt", FieldValue.serverTimestamp()).await()

        // 2) rilegge per tempi accurati (secondi reali)
        val stSnap = subRef.get().await()
        val st = stSnap.toObject(Subtask::class.java)
        val startedMs = st?.startedAt?.toDate()?.time
        val completedMs = st?.completedAt?.toDate()?.time
        val expectedMs = ((st?.expectedMinutes ?: expectedMin) * 60_000L)

        val actualMs: Long = when {
            startedMs != null && completedMs != null -> (completedMs - startedMs).coerceAtLeast(0L)
            startedMs != null -> (System.currentTimeMillis() - startedMs).coerceAtLeast(0L)
            else -> (actualMin * 60_000L).coerceAtLeast(0L)
        }

        // 3) alert se in ritardo
        if (actualMs > expectedMs) {
            val doc = db.collection("alerts").document()
            val a = Alert(
                id = doc.id,
                caregiverId = caregiverId,
                userId = userId,
                activityId = activityId,
                subtaskId = subtaskId,
                minutesExpected = (expectedMs / 60_000L).toInt(),
                minutesActual = (actualMs / 60_000L).toInt()
            )
            doc.set(a).await()
        }

        // 4) calcola e salva medaglia
        val medal = medalFor(expectedMs, actualMs) // "GOLD"/"SILVER"/"BRONZE"/null
        subRef.update("medal", medal).await()

        // 5) aggiorna XP + conteggio badge
        if (medal != null) {
            val points = when (medal) {
                "GOLD" -> GOLD_POINTS
                "SILVER" -> SILVER_POINTS
                "BRONZE" -> BRONZE_POINTS
                else -> 0
            }
            awardBadgeAndXp(userId, medal, points)
        }

        // 6) se tutte complete → stato DONE + PLATINO se tutte GOLD
        val subs = subtasks(activityId)
        if (subs.all { it.completedAt != null }) {
            actRef.update(mapOf("status" to "DONE", "completedAt" to FieldValue.serverTimestamp())).await()

            val allGold = subs.isNotEmpty() && subs.all { it.medal == "GOLD" }
            if (allGold) {
                awardBadgeAndXp(userId, "PLATINUM", PLATINUM_POINTS)
            }
        }
    }

    // -------------------- SUBTASK: LOCATION --------------------

    suspend fun updateSubtaskLocation(activityId: String, subtaskId: String, lat: Double, lng: Double) {
        val geo = GeoPoint(lat, lng)
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
        imageUri: Uri?
    ) {
        val col = db.collection("activities").document(activityId)
            .collection("subtasks").document(subtaskId)
            .collection("comments")

        val commentDoc = col.document()
        var imageUrl: String? = null

        if (imageUri != null) {
            // Path compatibile con rules: comments/{activityId}/{subtaskId}/{commentId}.jpg
            val ref = storage.reference
                .child("comments/$activityId/$subtaskId/${commentDoc.id}.jpg")

            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build()

            ref.putFile(imageUri, metadata).await()
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

    // -------------------- CHAT --------------------

    fun chatId(a: String, b: String) = listOf(a, b).sorted().joinToString("_")

    // Repo.sendMessage
    suspend fun sendMessage(fromId: String, toId: String, text: String) {
        val cId = chatId(fromId, toId)
        val doc = db.collection("chats").document(cId)
            .collection("messages").document()

        val data = mapOf(
            "id" to doc.id,
            "chatId" to cId,
            "fromId" to fromId,
            "toId" to toId,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp(), // server time (può essere null al primo snapshot)
            "createdAtClient" to Timestamp.now()         // fallback immediato per l’ordinamento/visibilità
        )

        doc.set(data).await()
        db.collection("chats").document(cId)
            .set(mapOf("lastAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
    }

    // -------------------- GAMIFICATION CORE --------------------

    /**
     * Aggiorna contatori badge e XP/level.
     * badge: "GOLD" | "SILVER" | "BRONZE" | "PLATINUM" | null
     * points: XP da applicare.
     *
     * NB: non dipende dalla data class Gamification — legge/scrive campi come mappa,
     * così compila anche se il tuo model ha nomi/shape diversi.
     */
    private suspend fun awardBadgeAndXp(userId: String, badge: String?, points: Int) {
        val ref = db.collection("gamification").document(userId)
        db.runTransaction { tr ->
            val snap = tr.get(ref)
            val cur = snap.data ?: emptyMap<String, Any?>()

            // contatori badge correnti
            val gold     = (cur["goldCount"]     as? Number)?.toInt() ?: 0
            val silver   = (cur["silverCount"]   as? Number)?.toInt() ?: 0
            val bronze   = (cur["bronzeCount"]   as? Number)?.toInt() ?: 0
            val platinum = (cur["platinumCount"] as? Number)?.toInt() ?: 0

            // livello/XPs correnti
            var level     = (cur["level"]     as? Number)?.toInt() ?: 1
            var xpInLevel = (cur["xpInLevel"] as? Number)?.toInt() ?: 0
            var totalXp   = (cur["totalXp"]   as? Number)?.toInt() ?: 0

            // incrementi
            val add = points.coerceAtLeast(0)
            xpInLevel += add
            totalXp   += add
            // compatibilità con UI che usa "points"
            val pointsTotal: Int = totalXp  // mantieni points = totalXp per compatibilità

            // level-up progressivo
            while (level < MAX_LEVEL && xpInLevel >= nextLevelCost(level)) {
                xpInLevel -= nextLevelCost(level)
                level++
            }
            if (level >= MAX_LEVEL) {
                level = MAX_LEVEL
                xpInLevel = 0
            }

            // aggiorna contatori badge
            val newGold     = gold     + if (badge == "GOLD") 1 else 0
            val newSilver   = silver   + if (badge == "SILVER") 1 else 0
            val newBronze   = bronze   + if (badge == "BRONZE") 1 else 0
            val newPlatinum = platinum + if (badge == "PLATINUM") 1 else 0

            // scrivi (merge) i nuovi valori
            tr.set(
                ref,
                mapOf(
                    "level"         to level,
                    "xpInLevel"     to xpInLevel,
                    "totalXp"       to totalXp,
                    "points"        to pointsTotal, // compat
                    "goldCount"     to newGold,
                    "silverCount"   to newSilver,
                    "bronzeCount"   to newBronze,
                    "platinumCount" to newPlatinum
                ),
                SetOptions.merge()
            )
        }.await()
    }

    // --- SUBTASKS: creazione e ricalcolo minuti previsti ---
    suspend fun addSubtask(activityId: String, sub: Subtask): String {
        val actRef = db.collection("activities").document(activityId)
        val subRef = actRef.collection("subtasks").document()
        val toSave = sub.copy(id = subRef.id)

        subRef.set(toSave).await()

        val snap = actRef.collection("subtasks").get().await()
        val totalExpected = snap.documents
            .mapNotNull { it.toObject(Subtask::class.java)?.expectedMinutes }
            .sum()

        actRef.update("expectedMinutes", totalExpected).await()
        return subRef.id
    }

    // --- SUBTASK IMAGE: upload e salvataggio URL ---
    suspend fun uploadSubtaskImage(activityId: String, subtaskId: String, uri: Uri): String {
        val ref = storage.reference
            .child("subtask_images/$activityId/$subtaskId.jpg")
        ref.putFile(uri).await()
        val url = ref.downloadUrl.await().toString()
        db.collection("activities").document(activityId)
            .collection("subtasks").document(subtaskId)
            .update("imageUrl", url).await()
        return url
    }

    // ---------- Utility ----------

    // Medaglia in base al delta rispetto al previsto (in ms)
    private fun medalFor(expectedMs: Long, actualMs: Long): String? {
        val delta = expectedMs - actualMs // >0 = prima del previsto
        return when {
            delta >= 60_000L -> "GOLD"                 // ≥ 1:00 prima
            delta in 30_000L..59_999L -> "SILVER"      // 0:30..0:59 prima
            delta in 0L..29_999L -> "BRONZE"           // 0:00..0:29 prima/puntuale
            else -> null                                // in ritardo
        }
    }

    // Salva il token in una subcollection sicura: users/{uid}/fcmTokens/{token}
    // IMPORT: com.google.firebase.firestore.ktx.firestore, kotlinx.coroutines.tasks.await, etc.

    suspend fun saveFcmTokenForCurrentUser(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // 1) Elimina questo token da QUALSIASI altro utente (duplice login sullo stesso device)
        val dupes = db.collectionGroup("fcmTokens")
            .whereEqualTo("token", token)
            .get()
            .await()

        for (doc in dupes.documents) {
            // parent.parent = /users/{uidDelDoc}
            val ownerUid = doc.reference.parent.parent?.id
            if (ownerUid != null && ownerUid != uid) {
                doc.reference.delete()
            }
        }

        // 2) Upsert sotto l'utente corrente, con docId = token (così è idempotente)
        val ref = db.collection("users").document(uid)
            .collection("fcmTokens").document(token)

        ref.set(
            mapOf(
                "token" to token,
                "platform" to "android",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }
}