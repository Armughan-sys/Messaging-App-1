package com.fightclub.attendance.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.fightclub.attendance.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Opens WhatsApp directly on a contact's chat with a message pre-filled in the input box. */
@Singleton
class WhatsAppLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun isWhatsAppInstalled(): Boolean =
        runCatching { context.packageManager.getPackageInfo(Constants.WHATSAPP_PACKAGE_NAME, 0) }
            .isSuccess

    /**
     * Uses WhatsApp's documented "click to chat" deep link (the same mechanism businesses use)
     * rather than [Intent.ACTION_SEND], because `ACTION_SEND` only pre-fills WhatsApp's chat
     * *list* (asking the user to pick a conversation), while this opens the target chat directly
     * — which [WhatsAppAccessibilityService] then needs in order to find the right message box.
     */
    fun openChatWithPrefilledMessage(phoneNumber: String, message: String) {
        val digitsOnly = phoneNumber.filter { it.isDigit() }
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$digitsOnly&text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(Constants.WHATSAPP_PACKAGE_NAME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
