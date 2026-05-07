package com.pocketpet.core.ai

import com.pocketpet.core.notifications.NotificationAction
import com.pocketpet.core.notifications.NotificationItem

class IntentRecognizer {

    sealed interface Intent {
        data object Summarise : Intent
        data object Urgent : Intent
        data class DismissNotification(val hint: String) : Intent
        data class ReplyNotification(val hint: String, val replyText: String?) : Intent
        data class OpenNotification(val hint: String) : Intent
        data object Unknown : Intent
    }

    fun recognise(text: String): Intent {
        val t = text.lowercase().trim()
        return when {
            t.matches(Regex(".*(summaris|summariz|what.s new|notifications|update).*")) -> Intent.Summarise
            t.matches(Regex(".*(urgent|important|priority|critical).*")) -> Intent.Urgent
            t.matches(Regex(".*dismiss.*")) -> Intent.DismissNotification(extractHint(t, "dismiss"))
            t.matches(Regex(".*reply.*|.*respond.*")) -> {
                val hint = extractHint(t, "reply", "respond")
                val replyText = extractQuote(t)
                Intent.ReplyNotification(hint, replyText)
            }
            t.matches(Regex(".*open.*|.*show.*")) -> Intent.OpenNotification(extractHint(t, "open", "show"))
            else -> Intent.Unknown
        }
    }

    fun parseActionFromLlmResponse(
        llmOutput: String,
        notifications: List<NotificationItem>
    ): NotificationAction? {
        val line = llmOutput.lines().firstOrNull { it.contains(Regex("(DISMISS|REPLY|OPEN|SNOOZE)", RegexOption.IGNORE_CASE)) }
            ?: return null

        val idMatch = Regex("""id="([^"]+)"""").find(line)
        val id = idMatch?.groupValues?.get(1) ?: return null

        return when {
            line.contains("DISMISS", ignoreCase = true) -> NotificationAction.Dismiss(id)
            line.contains("REPLY", ignoreCase = true) -> {
                val replyMatch = Regex("""reply="([^"]+)"""").find(line)
                val reply = replyMatch?.groupValues?.get(1) ?: return null
                NotificationAction.Reply(id, reply)
            }
            line.contains("OPEN", ignoreCase = true) -> NotificationAction.Open(id)
            line.contains("SNOOZE", ignoreCase = true) -> NotificationAction.Snooze(id, 30 * 60 * 1000L)
            else -> null
        }
    }

    private fun extractHint(text: String, vararg keywords: String): String {
        var t = text
        keywords.forEach { kw -> t = t.replace(kw, "").trim() }
        return t.replace(Regex("^(the|a|an)\\s+"), "").trim()
    }

    private fun extractQuote(text: String): String? {
        val match = Regex("""["'](.+?)["']""").find(text)
        return match?.groupValues?.get(1)
    }
}
