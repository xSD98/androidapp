package com.example.tieniiltempo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.core.graphics.scale

data class CommentDoc(
    val id: String = "",
    val authorId: String = "",
    val authorRole: String = "",
    val text: String = "",
    val imageData: String? = null,  // data URL base64
    val createdAt: Timestamp? = null
)

@Composable
fun CommentsScreen(
    activityId: String,
    subtaskId: String,
    myUid: String,
    myRole: String,                 // "user" | "caregiver"
    onBack: () -> Unit
) {
    val db = remember { Firebase.firestore }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var comments by remember { mutableStateOf(listOf<CommentDoc>()) }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var uploading by remember { mutableStateOf(false) }

    // Photo Picker
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> pickedUri = uri }

    // Realtime listener
    LaunchedEffect(activityId, subtaskId) {
        db.collection("activities").document(activityId)
            .collection("subtasks").document(subtaskId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                comments = snap?.documents?.map {
                    CommentDoc(
                        id = it.id,
                        authorId = it.getString("authorId") ?: "",
                        authorRole = it.getString("authorRole") ?: "",
                        text = it.getString("text") ?: "",
                        imageData = it.getString("imageData"),
                        createdAt = it.getTimestamp("createdAt")
                    )
                } ?: emptyList()
            }
    }

    // --- helper: compress & to data URL ---
    suspend fun compressToDataUrl(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            ctx.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return@withContext null
                val original = BitmapFactory.decodeStream(input) ?: return@withContext null

                val maxSide = 1024
                val ratio = max(original.width, original.height).toFloat() / maxSide
                val scaled = if (ratio > 1f) {
                    val w = (original.width / ratio).roundToInt()
                    val h = (original.height / ratio).roundToInt()
                    original.scale(w, h)
                } else original

                var quality = 80
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                var bytes = baos.toByteArray()
                while (bytes.size > 800_000 && quality > 40) {
                    baos.reset()
                    quality -= 10
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    bytes = baos.toByteArray()
                }
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                "data:image/jpeg;base64,$b64"
            }
        } catch (_: Exception) { null }
    }

    // --- invio commento ---
    suspend fun sendComment() {
        if (input.text.isBlank() && pickedUri == null) return
        uploading = true

        val dataUrl = if (pickedUri != null) compressToDataUrl(pickedUri!!) else null
        if (pickedUri != null && dataUrl == null) {
            uploading = false
            Toast.makeText(ctx, "Immagine non valida", Toast.LENGTH_LONG).show()
            return
        }

        val col = db.collection("activities").document(activityId)
            .collection("subtasks").document(subtaskId)
            .collection("comments")

        val payload = hashMapOf(
            "authorId" to myUid,
            "authorRole" to myRole,
            "text" to input.text,
            "imageData" to dataUrl,
            "createdAt" to FieldValue.serverTimestamp()
        )

        col.add(payload)
            .addOnSuccessListener {
                input = TextFieldValue("")
                pickedUri = null
                uploading = false
            }
            .addOnFailureListener { e ->
                uploading = false
                Toast.makeText(ctx, "Invio fallito: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // --- UI ---
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Commenti", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("Indietro") }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(comments) { c ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (c.authorRole == "caregiver") "Caregiver" else "Utente",
                            style = MaterialTheme.typography.labelMedium)
                        if (c.text.isNotBlank()) Text(c.text)
                        if (!c.imageData.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            AsyncImage(
                                model = c.imageData,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(180.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Scrivi un commento…") }
        )
        Spacer(Modifier.height(8.dp))
        Row {
            OutlinedButton(
                onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) { Text(if (pickedUri == null) "Aggiungi foto" else "Foto selezionata") }

            Spacer(Modifier.width(12.dp))
            Button(
                enabled = !uploading,
                onClick = { scope.launch { sendComment() } }
            ) { Text(if (uploading) "Invio…" else "Invia") }
        }
    }
}
