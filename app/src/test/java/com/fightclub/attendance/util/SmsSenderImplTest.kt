package com.fightclub.attendance.util

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.telephony.SmsManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSmsManager

@RunWith(RobolectricTestRunner::class)
class SmsSenderImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val smsSender: SmsSender = SmsSenderImpl(context)

    @Test
    fun `sendSms reports Sent once every part's sent broadcast fires with RESULT_OK`() = runTest {
        val deferred = async(Dispatchers.Default) {
            smsSender.sendSms("+15551234567", "AoA Abdullah\nSaim and me won't be attending the class today")
        }

        // Let sendMultipartTextMessage register its receiver and stash the sent PendingIntents.
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(50)

        val shadowSmsManager: ShadowSmsManager = shadowOf(SmsManager.getDefault())
        val lastParams = shadowSmsManager.lastSentMultipartTextMessageParams
        assertTrue("Expected sendMultipartTextMessage to have been invoked", lastParams != null)

        lastParams.sentIntents.forEach { pendingIntent ->
            pendingIntent.send(Activity.RESULT_OK)
        }
        shadowOf(Looper.getMainLooper()).idle()

        val result = withContext(Dispatchers.Default) { deferred.await() }
        assertEquals(SmsSendResult.Sent, result)
    }

    @Test
    fun `sendSms reports Failed when any part's broadcast fires with a non-OK result`() = runTest {
        val deferred = async(Dispatchers.Default) {
            smsSender.sendSms("+15551234567", "short message")
        }

        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(50)

        val shadowSmsManager: ShadowSmsManager = shadowOf(SmsManager.getDefault())
        val lastParams = shadowSmsManager.lastSentMultipartTextMessageParams

        lastParams.sentIntents.forEach { pendingIntent ->
            pendingIntent.send(SmsManager.RESULT_ERROR_GENERIC_FAILURE)
        }
        shadowOf(Looper.getMainLooper()).idle()

        val result = withContext(Dispatchers.Default) { deferred.await() }
        assertTrue(result is SmsSendResult.Failed)
    }
}
