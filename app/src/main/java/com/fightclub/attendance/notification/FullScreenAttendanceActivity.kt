package com.fightclub.attendance.notification

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.fightclub.attendance.R
import com.fightclub.attendance.domain.scheduler.AttendanceResponseHandler
import com.fightclub.attendance.ui.theme.FightClubAttendanceTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shown full-screen, including over the lock screen, for the Mon/Wed/Fri attendance question.
 * Mirrors the YES/NO notification actions so the user can answer either from here or directly
 * from the notification shade without unlocking the phone.
 */
@AndroidEntryPoint
class FullScreenAttendanceActivity : ComponentActivity() {

    @Inject
    lateinit var attendanceResponseHandler: AttendanceResponseHandler

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        setContent {
            FightClubAttendanceTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AttendancePromptScreen(
                        onYes = { respond(isAttending = true) },
                        onNo = { respond(isAttending = false) }
                    )
                }
            }
        }
    }

    private fun respond(isAttending: Boolean) {
        notificationHelper.cancelAttendancePrompt()
        lifecycleScope.launch {
            attendanceResponseHandler.handleResponse(isAttending)
            finish()
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)
    }
}

@Composable
private fun AttendancePromptScreen(onYes: () -> Unit, onNo: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.SportsMma,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Text(
                text = stringResource(R.string.attendance_prompt_question),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 40.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onNo) {
                    Text(stringResource(R.string.action_no))
                }
                Button(onClick = onYes) {
                    Text(stringResource(R.string.action_yes))
                }
            }
        }
    }
}
