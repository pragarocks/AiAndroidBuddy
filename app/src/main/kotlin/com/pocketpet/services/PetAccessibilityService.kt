package com.pocketpet.services

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.pocketpet.core.notifications.NotificationAction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PetAccessibilityService : AccessibilityService() {

    private val TAG = "PetAccessibilityService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private var _actionResults = MutableSharedFlow<ActionResult>(replay = 0)
        val actionResults: SharedFlow<ActionResult> = _actionResults.asSharedFlow()

        private var instance: PetAccessibilityService? = null

        fun getInstance(): PetAccessibilityService? = instance

        fun performAction(action: NotificationAction) {
            instance?.handleAction(action)
        }
    }

    data class ActionResult(val action: NotificationAction, val success: Boolean)

    override fun onServiceConnected() {
        instance = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We primarily use this for proactive notifications, but rely on the NLS for buffering
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    private fun handleAction(action: NotificationAction) {
        scope.launch {
            val success = when (action) {
                is NotificationAction.Dismiss -> dismissNotification(action.notificationKey)
                is NotificationAction.Reply -> replyToNotification(action.notificationKey, action.text)
                is NotificationAction.Open -> openNotification(action.notificationKey)
                is NotificationAction.Snooze -> snoozeNotification(action.notificationKey, action.durationMs)
            }
            _actionResults.emit(ActionResult(action, success))
        }
    }

    private fun dismissNotification(key: String): Boolean {
        return try {
            val node = findNotificationNode(key) ?: return false
            val dismissBtn = findChildByText(node, "dismiss", "clear", "swipe")
                ?: findChildByAction(node, AccessibilityNodeInfo.ACTION_DISMISS)
            dismissBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Dismiss failed: ${e.message}")
            false
        }
    }

    private fun replyToNotification(key: String, text: String): Boolean {
        return try {
            val node = findNotificationNode(key) ?: return false
            val replyInput = findEditableChild(node) ?: return false
            val bundle = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val textSet = replyInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            if (!textSet) return false
            val sendBtn = findChildByText(node, "send", "reply")
            sendBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Reply failed: ${e.message}")
            false
        }
    }

    private fun openNotification(key: String): Boolean {
        return try {
            val node = findNotificationNode(key) ?: return false
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (e: Exception) {
            Log.e(TAG, "Open failed: ${e.message}")
            false
        }
    }

    private fun snoozeNotification(key: String, durationMs: Long): Boolean {
        // Snooze is handled by re-posting the notification after duration via WorkManager
        // Dismiss it now and re-post later
        return dismissNotification(key)
    }

    private fun findNotificationNode(key: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return traverseForNotification(root, key)
    }

    private fun traverseForNotification(node: AccessibilityNodeInfo?, key: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.contains(key, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = traverseForNotification(child, key)
            if (found != null) return found
        }
        return null
    }

    private fun findChildByText(node: AccessibilityNodeInfo, vararg texts: String): AccessibilityNodeInfo? {
        for (text in texts) {
            val results = node.findAccessibilityNodeInfosByText(text)
            if (results.isNotEmpty()) return results[0]
        }
        return null
    }

    private fun findChildByAction(node: AccessibilityNodeInfo, action: Int): AccessibilityNodeInfo? {
        if (node.actionList.any { it.id == action }) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findChildByAction(child, action)
            if (found != null) return found
        }
        return null
    }

    private fun findEditableChild(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableChild(child)
            if (found != null) return found
        }
        return null
    }
}
