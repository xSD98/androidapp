package com.example.tieniiltempo

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.example.tieniiltempo.ui.screens.LoginScreen
import com.example.tieniiltempo.ui.screens.RoleGateScreen
import com.example.tieniiltempo.ui.screens.CaregiverDashboardScreen
import com.example.tieniiltempo.ui.screens.UserActivitiesScreen
import com.example.tieniiltempo.ui.screens.CreateActivityScreen
import com.example.tieniiltempo.ui.screens.RunnerScreen
import com.example.tieniiltempo.ui.screens.ChatListScreen
import com.example.tieniiltempo.ui.screens.ChatScreen
@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val startDest = if (Firebase.auth.currentUser == null) "login" else "gate"

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
                        // Se l'uid non arriva, torna al login
                        Firebase.auth.signOut()
                        nav.navigate("login") { popUpTo(0) }
                    }
                }
            )
        }

        // ----------------- DASHBOARD CAREGIVER -----------------
        composable("caregiver") {
            CaregiverDashboardScreen(
                openUser = { uid ->
                    if (uid.isNotBlank()) nav.navigate("userActivities/$uid")
                },
                openChatList = { nav.navigate("chatList") },
                onLogout = {
                    Firebase.auth.signOut()
                    nav.navigate("login") { popUpTo(0) }
                }
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
                    onRun = { activityId ->
                        if (activityId.isNotBlank()) nav.navigate("runner/$activityId")
                    },
                    openChat = { nav.navigate("chatList") },
                    onLogout = {
                        Firebase.auth.signOut()
                        nav.navigate("login") { popUpTo(0) }
                    }
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

        // ----------------- LISTA CHAT (scegli interlocutore) -----------------
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
    }
}
