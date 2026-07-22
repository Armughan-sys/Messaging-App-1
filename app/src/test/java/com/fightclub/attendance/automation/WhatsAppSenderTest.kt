package com.fightclub.attendance.automation

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.fightclub.attendance.util.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPackageManager

/**
 * Covers the parts of the WhatsApp send flow that are realistically testable off-device: the
 * "WhatsApp isn't installed" and "the accessibility service isn't turned on" guard clauses.
 * Simulating the actual on-screen tap of WhatsApp's Send button (see
 * [WhatsAppAccessibilityService]) requires a real WhatsApp window and is exercised manually /
 * via instrumented testing on a device instead — see the README for why.
 */
@RunWith(RobolectricTestRunner::class)
class WhatsAppSenderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val whatsAppLauncher = WhatsAppLauncher(context)
    private val sender: WhatsAppSender = WhatsAppSenderImpl(context, whatsAppLauncher)

    @Test
    fun `fails fast when WhatsApp is not installed`() = runTest {
        // No package is registered for com.whatsapp in this test's PackageManager by default.
        val result = sender.sendMessage("+15551234567", "AoA Abdullah")

        assertTrue(result is WhatsAppSendResult.Failed)
        assertTrue((result as WhatsAppSendResult.Failed).reason.contains("not installed"))
    }

    @Test
    fun `reports AccessibilityServiceDisabled when the service has never been enabled`() = runTest {
        installFakeWhatsApp()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")

        val result = sender.sendMessage("+15551234567", "AoA Abdullah")

        assertTrue(result is WhatsAppSendResult.AccessibilityServiceDisabled)
    }

    @Test
    fun `reports AccessibilityServiceDisabled when enabled in settings but not currently running`() = runTest {
        installFakeWhatsApp()
        val enabledComponent = ComponentName(context, WhatsAppAccessibilityService::class.java).flattenToString()
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            enabledComponent
        )

        // The setting says it's enabled, but no live AccessibilityService instance registered
        // itself (that only happens via the real system framework calling onServiceConnected),
        // which is exactly the "service was toggled off mid-session" case this guards against.
        val result = sender.sendMessage("+15551234567", "AoA Abdullah")

        assertTrue(result is WhatsAppSendResult.AccessibilityServiceDisabled)
    }

    @Test
    fun `AccessibilityUtils correctly parses a colon-separated multi-service setting`() {
        val target = ComponentName(context, WhatsAppAccessibilityService::class.java)
        val otherService = "com.example.other/.SomeAccessibilityService"
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "$otherService:${target.flattenToString()}"
        )

        assertTrue(
            AccessibilityUtils.isAccessibilityServiceEnabled(context, WhatsAppAccessibilityService::class.java)
        )
    }

    private fun installFakeWhatsApp() {
        val shadowPackageManager: ShadowPackageManager = shadowOf(context.packageManager)
        shadowPackageManager.installPackage(
            PackageInfo().apply { packageName = Constants.WHATSAPP_PACKAGE_NAME }
        )
    }
}
