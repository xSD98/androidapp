package com.example.tieniiltempo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tieniiltempo.core.RealtimeWatchers
import com.example.tieniiltempo.ui.screens.CaregiverDashboardScreen
import com.example.tieniiltempo.ui.screens.ChatListScreen
import com.example.tieniiltempo.ui.screens.ChatScreen
import com.example.tieniiltempo.ui.screens.CommentsScreen
import com.example.tieniiltempo.ui.screens.CreateActivityScreen
import com.example.tieniiltempo.ui.screens.GamificationScreen
import com.example.tieniiltempo.ui.screens.LoginScreen
import com.example.tieniiltempo.ui.screens.RoleGateScreen
import com.example.tieniiltempo.ui.screens.RunnerScreen
import com.example.tieniiltempo.ui.screens.UserActivitiesScreen
import com.example.tieniiltempo.ui.screens.EditActivityScreen
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.google.firebase.auth.FirebaseAuth
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import com.example.tieniiltempo.data.Repo
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
// 👇 AGGIUNTO per il deep link
import androidx.navigation.navDeepLink

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val nav = rememberNavController()
    val startDest = if (Firebase.auth.currentUser == null) "login" else "gate"

    // scope per usare launch dentro il callback del permission launcher
    val scope = rememberCoroutineScope()

    // Accendi/spegni i watcher realtime al cambio utente
    val auth = Firebase.auth
    val userState = remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { fa ->
            userState.value = fa.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    // --- Launcher per il permesso notifiche (Android 13+)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Se l’utente accetta/nega, proviamo comunque a prendere il token
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { t ->
                // salvo il token per l’utente loggato (se presente) usando lo scope composable
                scope.launch {
                    try { Repo.saveFcmTokenForCurrentUser(t) } catch (_: Exception) {}
                }
            }
    }

    val currentUid = userState.value?.uid
    LaunchedEffect(currentUid) {
        if (currentUid != null) {
            // start watchers realtime
            RealtimeWatchers.startAll(ctx, currentUid)

            // --- Registra token FCM e chiedi permesso se necessario ---
            if (Build.VERSION.SDK_INT >= 33) {
                val granted = ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                if (!granted) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // già concesso → salva token
                    val t = FirebaseMessaging.getInstance().token.await()
                    try { Repo.saveFcmTokenForCurrentUser(t) } catch (_: Exception) {}
                }
            } else {
                // Android <= 12: nessun permesso richiesto
                val t = FirebaseMessaging.getInstance().token.await()
                try { Repo.saveFcmTokenForCurrentUser(t) } catch (_: Exception) {}
            }
        } else {
            RealtimeWatchers.stopAll()
        }
    }

    // ascolta richieste di navigazione verso Commenti (inviate dal RunnerScreen via NavIntents)
    val pendingComments by NavIntents.toComments
    LaunchedEffect(pendingComments) {
        pendingComments?.let { (actId, stId) ->
            nav.navigate("comments/$actId/$stId")
            NavIntents.toComments.value = null // reset
        }
    }

    NavHost(navController = nav, startDestination = startDest) {

        // ----------------- LOGIN -----------------
        composable("login") {
            LoginScreen(
                onLoggedIn = {
                    nav.navigate("gate") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // ----------------- GATE (decide ruolo) -----------------
        composable("gate") {
            RoleGateScreen(
                onCaregiver = {
                    nav.navigate("caregiver") {
                        popUpTo("gate") { inclusive = true }
                    }
                },
                onUser = { uid ->
                    if (uid.isNotBlank()) {
                        nav.navigate("userActivities/$uid") {
                            popUpTo("gate") { inclusive = true }
                        }
                    } else {
                        Firebase.auth.signOut()
                        nav.navigate("login") { popUpTo(0) }
                    }
                }
            )
        }

        // ----------------- DASHBOARD CAREGIVER -----------------
        composable("caregiver") {
            CaregiverDashboardScreen(
                openUser = { uid -> if (uid.isNotBlank()) nav.navigate("userActivities/$uid") },
                openChatList = { nav.navigate("chatList") },
                onLogout = {
                    Firebase.auth.signOut()
                    nav.navigate("login") { popUpTo(0) }
                },
                openGamification = { uid -> nav.navigate("gamification/$uid") }
            )
        }

        // ----------------- ATTIVITÀ UTENTE -----------------
        composable(
            route = "userActivities/{uid}",
            arguments = listOf(navArgument("uid") { type = NavType.StringType })
        ) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid")
            if (uid.isNullOrBlank()) {
                nav.navigateUp()
            } else {
                UserActivitiesScreen(
                    userId = uid,
                    onBack = { nav.navigateUp() },
                    onCreate = { nav.navigate("createActivity/$uid") },
                    onRun = { activityId -> if (activityId.isNotBlank()) nav.navigate("runner/$activityId") },
                    openChat = { nav.navigate("chatList") },
                    onLogout = { Firebase.auth.signOut(); nav.navigate("login") { popUpTo(0) } },
                    openGamification = { nav.navigate("gamification/$uid") }
                )
            }
        }

        // ----------------- CREA ATTIVITÀ (CAREGIVER) -----------------
        composable(
            route = "createActivity/{uid}",
            arguments = listOf(navArgument("uid") { type = NavType.StringType })
        ) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid")
            if (uid.isNullOrBlank()) {
                nav.navigateUp()
            } else {
                CreateActivityScreen(
                    userId = uid,
                    onDone = { nav.navigateUp() },
                    onBack = { nav.navigateUp() }
                )
            }
        }

        // ----------------- RUNNER ATTIVITÀ -----------------
        composable(
            route = "runner/{activityId}",
            arguments = listOf(navArgument("activityId") { type = NavType.StringType })
        ) { backStackEntry ->
            val actId = backStackEntry.arguments?.getString("activityId")
            if (actId.isNullOrBlank()) {
                nav.navigateUp()
            } else {
                RunnerScreen(
                    activityId = actId,
                    onFinished = { nav.navigateUp() },
                    onBack = { nav.navigateUp() }
                )
            }
        }

        // ----------------- LISTA CHAT -----------------
        composable("chatList") {
            ChatListScreen(
                onBack = { nav.navigateUp() },
                openChat = { otherId ->
                    if (otherId.isNotBlank()) nav.navigate("chat/$otherId")
                }
            )
        }

        // ----------------- CHAT 1:1 -----------------
        composable(
            route = "chat/{withId}",
            arguments = listOf(navArgument("withId") { type = NavType.StringType })
        ) { backStackEntry ->
            val otherId = backStackEntry.arguments?.getString("withId")
            if (otherId.isNullOrBlank()) {
                nav.navigateUp()
            } else {
                ChatScreen(
                    withId = otherId,
                    onBack = { nav.navigateUp() }
                )
            }
        }

        // ----------------- COMMENTI SOTTO-ATTIVITÀ -----------------
        composable(
            route = "comments/{activityId}/{subtaskId}",
            arguments = listOf(
                navArgument("activityId") { type = NavType.StringType },
                navArgument("subtaskId")  { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val activityId = backStackEntry.arguments?.getString("activityId")!!
            val subtaskId  = backStackEntry.arguments?.getString("subtaskId")!!
            CommentsScreen(
                activityId = activityId,
                subtaskId  = subtaskId,
                onBack     = { nav.navigateUp() }
            )
        }

        // ----------------- GAMIFICATION -----------------
        composable(
            route = "gamification/{uid}",
            arguments = listOf(navArgument("uid") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("uid")!!
            GamificationScreen(
                userId = userId,
                onBack = { nav.navigateUp() }
            )
        }


        // ----------------- EDIT ACTIVITY -----------------
        composable(
            route = "editActivity/{activityId}",
            arguments = listOf(navArgument("activityId") { type = NavType.StringType })
        ) { back ->
            val id = back.arguments?.getString("activityId")!!
            EditActivityScreen(
                activityId = id,
                onBack = { nav.navigateUp() },
                onSaved = { nav.navigateUp() }
            )
        }
    }
}