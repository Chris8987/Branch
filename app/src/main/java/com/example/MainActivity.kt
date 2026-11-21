package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.authStateFlow
import com.example.ui.BranchWorkspaceScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.hub.ProjectHubScreen
import com.example.ui.theme.BranchTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BranchTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener updates reactively */ })
    } else {
        var activeBoardId by rememberSaveable(user.uid) { mutableStateOf<String?>(null) }

        if (activeBoardId == null) {
            key("hub_${user.uid}") {
                ProjectHubScreen(
                    currentUserId = user.uid,
                    userEmail = user.email,
                    onOpenProject = { boardId -> activeBoardId = boardId },
                )
            }
        } else {
            key("board_${user.uid}_$activeBoardId") {
                BranchWorkspaceScreen(
                    currentUserId = user.uid,
                    boardId = activeBoardId!!,
                    onBackToHub = { activeBoardId = null },
                )
            }
        }
    }
}
