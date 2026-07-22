package com.fightclub.attendance.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.fightclub.attendance.util.Constants
import kotlinx.coroutines.CompletableDeferred
import java.lang.ref.WeakReference

/** What happened when the service tried to tap WhatsApp's Send button. */
sealed class AutomationResult {
    data object Sent : AutomationResult()
    data class Failed(val reason: String) : AutomationResult()
}

private data class PendingSendRequest(
    val expectedMessageSnippet: String,
    val deferred: CompletableDeferred<AutomationResult>
)

/**
 * Reads WhatsApp's own on-screen UI (via the Accessibility API) and taps its Send button on the
 * app's behalf, since WhatsApp has no API that lets a third-party app send a message
 * programmatically. The user must turn this on manually in system Settings ▸ Accessibility — it
 * cannot be requested like a normal runtime permission.
 *
 * This is inherently fragile: it depends on WhatsApp's internal view IDs, which are not a public,
 * stable API and can change with a WhatsApp update. [findMessageInputNode] and
 * [findSendButtonNode] fall back to a class/content-description search if the known resource IDs
 * are missing, but a future WhatsApp release could still break this outright — see the README.
 */
class WhatsAppAccessibilityService : AccessibilityService() {

    @Volatile
    private var pendingRequest: PendingSendRequest? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instanceRef?.get() === this) {
            instanceRef = null
        }
        pendingRequest = null
    }

    override fun onInterrupt() {
        completePending(AutomationResult.Failed("Accessibility service was interrupted by the system"))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val request = pendingRequest ?: return
        if (event?.packageName?.toString() != Constants.WHATSAPP_PACKAGE_NAME) return

        val root = rootInActiveWindow ?: return
        attemptSend(root, request)
    }

    private fun attemptSend(root: AccessibilityNodeInfo, request: PendingSendRequest) {
        // WhatsApp occasionally shows a one-time "Continue to chat" interstitial before landing
        // on the conversation itself when opened via a wa.me/api.whatsapp.com deep link.
        findContinueToChatButton(root)?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }

        val inputNode = findMessageInputNode(root) ?: return
        val sendNode = findSendButtonNode(root) ?: return

        val currentText = inputNode.text?.toString().orEmpty()
        if (!currentText.contains(request.expectedMessageSnippet.take(TEXT_MATCH_PREFIX_LENGTH))) {
            // Either the wrong chat is open, or the text hasn't finished populating yet — keep
            // waiting for the next content-changed event rather than guessing.
            return
        }

        val clicked = sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        completePending(
            if (clicked) AutomationResult.Sent else AutomationResult.Failed("Found WhatsApp's Send button but the tap failed")
        )
    }

    private fun findContinueToChatButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findNodeByText(root, "continue to chat")

    private fun findMessageInputNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        root.findAccessibilityNodeInfosByViewId("$WHATSAPP_PACKAGE:id/entry").firstOrNull()
            ?: findNodeByClassName(root, "android.widget.EditText")

    private fun findSendButtonNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        root.findAccessibilityNodeInfosByViewId("$WHATSAPP_PACKAGE:id/send").firstOrNull()
            ?: findNodeByContentDescription(root, "send")

    private fun findNodeByText(root: AccessibilityNodeInfo, needle: String): AccessibilityNodeInfo? {
        if (root.text?.toString()?.contains(needle, ignoreCase = true) == true && root.isClickable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            findNodeByText(child, needle)?.let { return it }
        }
        return null
    }

    private fun findNodeByClassName(root: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
        if (root.className?.toString() == className) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            findNodeByClassName(child, className)?.let { return it }
        }
        return null
    }

    private fun findNodeByContentDescription(root: AccessibilityNodeInfo, needle: String): AccessibilityNodeInfo? {
        if (root.contentDescription?.toString()?.contains(needle, ignoreCase = true) == true && root.isClickable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            findNodeByContentDescription(child, needle)?.let { return it }
        }
        return null
    }

    private fun completePending(result: AutomationResult) {
        val request = pendingRequest ?: return
        pendingRequest = null
        request.deferred.complete(result)
    }

    companion object {
        private const val WHATSAPP_PACKAGE = Constants.WHATSAPP_PACKAGE_NAME
        private const val TEXT_MATCH_PREFIX_LENGTH = 24

        @Volatile
        private var instanceRef: WeakReference<WhatsAppAccessibilityService>? = null

        fun isRunning(): Boolean = instanceRef?.get() != null

        /**
         * Arms the running service to click Send as soon as the foreground WhatsApp chat's
         * message box contains [expectedMessage]. Returns null if the service isn't currently
         * running (the user hasn't turned it on in system Accessibility settings).
         */
        fun requestSend(expectedMessage: String): CompletableDeferred<AutomationResult>? {
            val service = instanceRef?.get() ?: return null
            val deferred = CompletableDeferred<AutomationResult>()
            val request = PendingSendRequest(expectedMessage, deferred)
            service.pendingRequest = request

            // The relevant window may already be on screen (e.g. WhatsApp was already open),
            // in which case no new AccessibilityEvent will fire — so try immediately too.
            service.rootInActiveWindow?.let { service.attemptSend(it, request) }
            return deferred
        }

        fun cancelPendingRequest() {
            instanceRef?.get()?.pendingRequest = null
        }
    }
}
