package com.fightclub.attendance.automation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

sealed class WhatsAppSendResult {
    data object Sent : WhatsAppSendResult()
    data class Failed(val reason: String) : WhatsAppSendResult()

    /** Distinct from [Failed] so callers can offer a direct shortcut to the Accessibility settings screen. */
    data object AccessibilityServiceDisabled : WhatsAppSendResult()
}

/**
 * Sends the attendance message via WhatsApp instead of SMS. WhatsApp has no API a third-party app
 * can call to send a message on the user's behalf, so this opens the target chat with the message
 * pre-filled (see [WhatsAppLauncher]) and then relies on [WhatsAppAccessibilityService] to find
 * and tap WhatsApp's own Send button — which only works if the user has turned that service on in
 * system Settings ▸ Accessibility.
 */
interface WhatsAppSender {
    suspend fun sendMessage(phoneNumber: String, message: String): WhatsAppSendResult
}

@Singleton
class WhatsAppSenderImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val whatsAppLauncher: WhatsAppLauncher
) : WhatsAppSender {

    override suspend fun sendMessage(phoneNumber: String, message: String): WhatsAppSendResult {
        if (!whatsAppLauncher.isWhatsAppInstalled()) {
            return WhatsAppSendResult.Failed("WhatsApp is not installed on this device")
        }
        if (!AccessibilityUtils.isAccessibilityServiceEnabled(context, WhatsAppAccessibilityService::class.java)) {
            return WhatsAppSendResult.AccessibilityServiceDisabled
        }

        // Arm the accessibility service *before* opening the chat so it can't miss the
        // window-content-changed event that fires as soon as the chat finishes loading.
        val deferred = WhatsAppAccessibilityService.requestSend(message)
            ?: return WhatsAppSendResult.AccessibilityServiceDisabled

        whatsAppLauncher.openChatWithPrefilledMessage(phoneNumber, message)

        val result = withTimeoutOrNull(SEND_TIMEOUT_MS) { deferred.await() }
        if (result == null) {
            WhatsAppAccessibilityService.cancelPendingRequest()
            return WhatsAppSendResult.Failed(
                "Timed out waiting for WhatsApp — the message may still be sitting unsent in the chat."
            )
        }

        return when (result) {
            is AutomationResult.Sent -> WhatsAppSendResult.Sent
            is AutomationResult.Failed -> WhatsAppSendResult.Failed(result.reason)
        }
    }

    private companion object {
        const val SEND_TIMEOUT_MS = 15_000L
    }
}
