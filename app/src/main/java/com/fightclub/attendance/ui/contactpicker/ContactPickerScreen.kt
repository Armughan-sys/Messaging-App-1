package com.fightclub.attendance.ui.contactpicker

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fightclub.attendance.R
import com.fightclub.attendance.data.model.SavedContact
import com.fightclub.attendance.util.Constants

/**
 * Shown when the automatic search for "Abdullah Malik KAK" found zero or multiple candidates.
 * Lets the user either pick one of the ambiguous matches, or open the full phonebook picker to
 * choose the correct contact manually. The chosen contact is then saved permanently.
 */
@Composable
fun ContactPickerScreen(
    candidates: List<SavedContact>,
    onContactResolved: (Uri) -> Unit,
    onCandidateSelected: (SavedContact) -> Unit,
    modifier: Modifier = Modifier
) {
    // A raw ACTION_PICK against the Phone URI (rather than the higher-level PickContact
    // contract, which targets Contacts.CONTENT_URI) so the returned URI already resolves
    // directly to a phone number in ContactRepository.resolveContact.
    val pickContactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let(onContactResolved)
        }
    }

    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.contact_picker_title, Constants.TARGET_CONTACT_DISPLAY_NAME),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = if (candidates.isEmpty()) {
                    stringResource(R.string.contact_picker_none_found)
                } else {
                    stringResource(R.string.contact_picker_multiple_found)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            if (candidates.isNotEmpty()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(candidates) { candidate ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(candidate.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(candidate.phoneNumber, style = MaterialTheme.typography.bodyMedium)
                                Button(
                                    onClick = { onCandidateSelected(candidate) },
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text(stringResource(R.string.contact_picker_use_this_contact))
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_PICK,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                    )
                    pickContactLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.contact_picker_open_phonebook))
            }
        }
    }
}
