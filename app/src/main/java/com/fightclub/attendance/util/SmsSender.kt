package com.fightclub.attendance.util

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed class SmsSendResult {
    data object Sent : SmsSendResult()
    data class Failed(val reason: String) : SmsSendResult()
}

/**
 * Thin, testable wrapper around [SmsManager]. Splits long messages into multiple parts and waits
 * for the platform's own "sent" broadcast for every part before reporting success, so the app
 * never claims delivery it can't actually confirm was handed off to the carrier.
 */
interface SmsSender {
    suspend fun sendSms(phoneNumber: String, message: String): SmsSendResult
}

@Singleton
class SmsSenderImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SmsSender {

    override suspend fun sendSms(phoneNumber: String, message: String): SmsSendResult {
        val smsManager = getSmsManager()
        val parts = smsManager.divideMessage(message)

        return suspendCancellableCoroutine { continuation ->
            val action = "$SENT_ACTION_PREFIX.${System.nanoTime()}"
            val pendingResults = mutableListOf<Int>()
            var partsRemaining = parts.size

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(recvContext: Context, intent: Intent) {
                    pendingResults += resultCode
                    partsRemaining--
                    if (partsRemaining > 0) return

                    context.unregisterReceiver(this)
                    if (continuation.isActive) {
                        val result = if (pendingResults.all { it == Activity.RESULT_OK }) {
                            SmsSendResult.Sent
                        } else {
                            SmsSendResult.Failed(describeFailure(pendingResults))
                        }
                        continuation.resume(result)
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            val sentIntents = parts.indices.map {
                val intent = Intent(action).apply { setPackage(context.packageName) }
                android.app.PendingIntent.getBroadcast(
                    context,
                    it,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            }.toCollection(ArrayList())

            continuation.invokeOnCancellation {
                runCatching { context.unregisterReceiver(receiver) }
            }

            try {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
            } catch (e: Exception) {
                runCatching { context.unregisterReceiver(receiver) }
                if (continuation.isActive) {
                    continuation.resume(SmsSendResult.Failed(e.message ?: "Unknown error sending SMS"))
                }
            }
        }
    }

    private fun getSmsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    private fun describeFailure(resultCodes: List<Int>): String {
        val firstFailure = resultCodes.firstOrNull { it != Activity.RESULT_OK } ?: return "Unknown SMS failure"
        return when (firstFailure) {
            SmsManager.RESULT_ERROR_NO_SERVICE -> "No cellular service available"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "Airplane mode / radio is off"
            SmsManager.RESULT_ERROR_NULL_PDU -> "Internal SMS error (null PDU)"
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure sending SMS"
            else -> "SMS failed with result code $firstFailure"
        }
    }

    private companion object {
        const val SENT_ACTION_PREFIX = "com.fightclub.attendance.SMS_SENT"
    }
}
