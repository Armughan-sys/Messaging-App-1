package com.fightclub.attendance.ui.permissions

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fightclub.attendance.R

/** One runtime permission this app requests, with a plain-language reason shown to the user. */
data class PermissionRationale(
    val permission: String,
    val icon: ImageVector,
    val titleRes: Int,
    val explanationRes: Int
)

fun requiredRuntimePermissions(): List<PermissionRationale> = buildList {
    add(
        PermissionRationale(
            permission = Manifest.permission.SEND_SMS,
            icon = Icons.Filled.Sms,
            titleRes = R.string.permission_sms_title,
            explanationRes = R.string.permission_sms_explanation
        )
    )
    add(
        PermissionRationale(
            permission = Manifest.permission.READ_CONTACTS,
            icon = Icons.Filled.Contacts,
            titleRes = R.string.permission_contacts_title,
            explanationRes = R.string.permission_contacts_explanation
        )
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(
            PermissionRationale(
                permission = Manifest.permission.POST_NOTIFICATIONS,
                icon = Icons.Filled.NotificationsActive,
                titleRes = R.string.permission_notifications_title,
                explanationRes = R.string.permission_notifications_explanation
            )
        )
    }
}

@Composable
fun PermissionsScreen(
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rationales = requiredRuntimePermissions()

    Scaffold(modifier = modifier) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp)) {
            Icon(
                imageVector = Icons.Filled.RestartAlt,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = stringResource(R.string.permissions_screen_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.permissions_screen_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(rationales) { rationale ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Icon(
                                imageVector = rationale.icon,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(rationale.titleRes),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(rationale.explanationRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.permissions_grant_button))
            }
        }
    }
}
