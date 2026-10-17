package com.cb.backlog

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cb.backlog.ui.AuthScreen
import com.cb.backlog.ui.BacklogScreen
import com.cb.backlog.ui.DetailsScreen
import com.cb.backlog.ui.MainState
import com.cb.backlog.ui.MainViewModel
import com.cb.backlog.ui.SearchScreen
import com.cb.backlog.ui.SetupScreen
import com.cb.backlog.ui.UpdateChecker
import com.cb.backlog.ui.UpdateInfo
import com.cb.backlog.ui.theme.CBTheme
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CBApp).container
        setContent {
            CBTheme {
                CBRoot(container)
            }
        }
    }
}

@Composable
private fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Доступно обновление") },
        text = { Text("Новая версия: ${info.versionName}. Сейчас установлена ${BuildConfig.VERSION_NAME}.\n\nОткроется браузер — скачайте APK и установите его.") },
        confirmButton = {
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)))
            }) { Text("Скачать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Позже") }
        },
    )
}

@Composable
private fun CBRoot(container: com.cb.backlog.di.AppContainer) {
    val mainViewModel: MainViewModel = viewModel { MainViewModel(container.supabaseRepo) }
    val state by mainViewModel.state.collectAsStateWithLifecycle()

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        updateInfo = UpdateChecker.check(BuildConfig.UPDATE_REPO, BuildConfig.VERSION_NAME)
    }
    updateInfo?.let { UpdateDialog(it, onDismiss = { updateInfo = null }) }

    val navController = rememberNavController()

    LaunchedEffect(state) {
        val target = when (state) {
            MainState.Loading -> null
            MainState.NotAuthed -> "auth"
            MainState.NeedSetup -> "setup"
            is MainState.Ready -> "backlog"
        }
        if (target != null) {
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    if (state is MainState.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(navController = navController, startDestination = "auth") {
        composable("auth") {
            AuthScreen(container = container, mainViewModel = mainViewModel)
        }
        composable("setup") {
            SetupScreen(container = container, mainViewModel = mainViewModel)
        }
        composable("backlog") {
            BacklogScreen(
                container = container,
                onOpenSearch = { navController.navigate("search") },
                onOpenItem = { id -> navController.navigate("details/$id") },
                onSignOut = { mainViewModel.signOut() },
            )
        }
        composable("search") {
            SearchScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable("details/{itemId}") { entry ->
            val itemId = entry.arguments?.getString("itemId").orEmpty()
            DetailsScreen(
                container = container,
                itemId = itemId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
