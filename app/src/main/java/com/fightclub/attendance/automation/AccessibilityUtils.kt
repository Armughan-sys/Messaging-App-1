package com.fightclub.attendance.automation

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object AccessibilityUtils {

    /**
     * Checks the system's `ENABLED_ACCESSIBILITY_SERVICES` setting directly rather than via
     * [android.view.accessibility.AccessibilityManager], since the latter requires the calling
     * app to already be bound, which isn't guaranteed at the point this is checked (e.g. from a
     * background worker deciding whether it's worth even trying to send).
     */
    fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService>
    ): Boolean {
        val expected = ComponentName(context, serviceClass)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val candidate = ComponentName.unflattenFromString(splitter.next())
            if (candidate == expected) return true
        }
        return false
    }
}
