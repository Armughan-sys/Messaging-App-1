package com.fightclub.attendance.ui.navigation

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fightclub.attendance.ui.MainViewModel
import com.fightclub.attendance.ui.contactpicker.ContactPickerScreen
import com.fightclub.attendance.ui.common.LoadingScreen
import com.fightclub.attendance.ui.ContactSearchState
import com.fightclub.attendance.ui.home.HomeScreen
import com.fightclub.attendance.ui.permissions.PermissionsScreen
import com.fightclub.attendance.ui.permissions.requiredRuntimePermissions
import com.fightclub.attendance.ui.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

private fun allRuntimePermissionsGranted(context: Context): Boolean =
    requiredRuntimePermissions().all {
        ContextCompat.checkSelfPermission(context, it.permission) == PackageManager.PERMISSION_GRANTED
    }

/**
 * Top-level app composable. Gates the real navigation graph behind two prerequisites:
 * 1. All runtime permissions granted (shows [PermissionsScreen] otherwise).
 * 2. The manager contact resolved, automatically searching for "Abdullah Malik KAK" on first
 *    launch and falling back to [ContactPickerScreen] if that search is ambiguous.
 */
@Composable
fun FightClubApp(mainViewModel: MainViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val settings by mainViewModel.settings.collectAsStateWithLifecycle()
    val contactSearchState by mainViewModel.contactSearchState.collectAsStateWithLifecycle()

    var permissionsGranted by remember { mutableStateOf(allRuntimePermissionsGranted(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionsGranted = allRuntimePermissionsGranted(context)
    }

    // Permissions can also be granted from system Settings while the app is backgrounded, so
    // re-check whenever the app resumes rather than relying solely on the launcher callback.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsGranted = allRuntimePermissionsGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(permissionsGranted, settings) {
        if (permissionsGranted) {
            mainViewModel.runAutoContactSearchIfNeeded(settings)
        }
    }

    when {
        !permissionsGranted -> PermissionsScreen(
            onRequestPermissions = {
                permissionLauncher.launch(requiredRuntimePermissions().map { it.permission }.toTypedArray())
            }
        )

        contactSearchState is ContactSearchState.Searching -> LoadingScreen()

        contactSearchState is ContactSearchState.NeedsManualSelection -> ContactPickerScreen(
            candidates = (contactSearchState as ContactSearchState.NeedsManualSelection).candidates,
            onContactResolved = mainViewModel::onContactUriResolved,
            onCandidateSelected = mainViewModel::onCandidateSelected
        )

        else -> MainNavHost()
    }
}

@Composable
private fun MainNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onNavigateToSettings = { navController.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
