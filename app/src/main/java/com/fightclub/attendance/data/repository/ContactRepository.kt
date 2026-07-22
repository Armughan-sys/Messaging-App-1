package com.fightclub.attendance.data.repository

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.fightclub.attendance.data.model.SavedContact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps every [ContactsContract] interaction needed to find "Abdullah Malik KAK" automatically
 * on first launch, and to resolve a contact the user picks manually as a fallback.
 *
 * Requires [android.Manifest.permission.READ_CONTACTS] to have been granted; callers are
 * responsible for checking that beforehand.
 */
interface ContactRepository {
    /** Searches the phonebook for contacts whose display name matches [displayName]. */
    suspend fun searchByDisplayName(displayName: String): List<SavedContact>

    /** Resolves a contact picked via [android.provider.ContactsContract.Contacts.CONTENT_URI] (or Phone URI). */
    suspend fun resolveContact(contactUri: Uri): SavedContact?
}

@Singleton
class ContactRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ContactRepository {

    override suspend fun searchByDisplayName(displayName: String): List<SavedContact> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<SavedContact>()
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            )
            val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
            val selectionArgs = arrayOf("%$displayName%")

            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                val lookupIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
                val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val hasPhoneIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                while (cursor.moveToNext()) {
                    if (cursor.getInt(hasPhoneIndex) <= 0) continue
                    val contactId = cursor.getString(idIndex)
                    val lookupKey = cursor.getString(lookupIndex)
                    val name = cursor.getString(nameIndex) ?: continue
                    val phoneNumber = queryFirstPhoneNumber(contactId) ?: continue
                    results += SavedContact(lookupKey = lookupKey, displayName = name, phoneNumber = phoneNumber)
                }
            }
            results
        }

    override suspend fun resolveContact(contactUri: Uri): SavedContact? =
        withContext(Dispatchers.IO) {
            // ACTION_PICK against Phone.CONTENT_URI returns a data row URI, so query it directly
            // for the phone number and the owning contact id.
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@withContext null
                val contactId = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                )
                val name = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                ) ?: return@withContext null
                val number = cursor.getString(
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                ) ?: return@withContext null
                val lookupKey = queryLookupKey(contactId) ?: contactId
                return@withContext SavedContact(lookupKey = lookupKey, displayName = name, phoneNumber = number)
            }
            null
        }

    private fun queryFirstPhoneNumber(contactId: String): String? {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            }
        }
        return null
    }

    private fun queryLookupKey(contactId: String): String? {
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
            }
        }
        return null
    }
}
