package com.fightclub.attendance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fightclub.attendance.ui.navigation.FightClubApp
import com.fightclub.attendance.ui.theme.FightClubAttendanceTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the whole Compose UI. All navigation, permission onboarding, and
 * first-launch contact resolution happen inside [FightClubApp].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val settings by mainViewModel.settings.collectAsStateWithLifecycle()

            FightClubAttendanceTheme(themeMode = settings.themeMode) {
                FightClubApp(mainViewModel = mainViewModel)
            }
        }
    }
}
