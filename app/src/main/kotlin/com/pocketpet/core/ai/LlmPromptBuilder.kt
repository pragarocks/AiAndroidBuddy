package com.pocketpet.core.ai

import com.pocketpet.core.notifications.NotificationItem
import com.pocketpet.core.personality.PetProfile

class LlmPromptBuilder {

    fun buildSystemPrompt(profile: PetProfile, userName: String = "the user"): String {
        return buildString {
            appendLine("You are ${profile.name}, a ${profile.species} living inside $userName's Android phone.")
            appendLine("Personality: ${profile.speechStyle}. Peak trait: ${profile.peakStat.name}.")
            appendLine("Rules:")
            appendLine("- Respond in max 2 short sentences")
            appendLine("- Use first person (\"I noticed...\", \"Your...\")")
            appendLine("- Never mention being an AI")
            appendLine("- If urgent notification: say so first")
            if (profile.systemPromptSuffix.isNotBlank()) {
                appendLine(profile.systemPromptSuffix)
            }
        }.trimEnd()
    }

    fun buildSummarisePrompt(
        profile: PetProfile,
        notifications: List<NotificationItem>,
        userName: String = "the user"
    ): String {
        val systemPrompt = buildSystemPrompt(profile, userName)
        val notifJson = notifications.take(10).joinToString("\n") { n ->
            "- [${n.appLabel}] ${n.title.orEmpty()}: ${n.text?.take(100).orEmpty()}"
        }
        return buildString {
            appendLine(systemPrompt)
            appendLine()
            appendLine("Recent notifications (newest first):")
            appendLine(notifJson)
            appendLine()
            append("Task: Summarise the top 3 most important in 2 sentences. Flag any that need immediate action. Stay in character as ${profile.name}.")
        }
    }

    fun buildReplyPrompt(
        profile: PetProfile,
        notification: NotificationItem,
        userRequest: String
    ): String {
        val systemPrompt = buildSystemPrompt(profile)
        return buildString {
            appendLine(systemPrompt)
            appendLine()
            appendLine("Notification from ${notification.appLabel}:")
            appendLine("Title: ${notification.title.orEmpty()}")
            appendLine("Message: ${notification.text?.take(200).orEmpty()}")
            appendLine()
            append("User said: \"$userRequest\"\nDraft a short, natural reply. Max 1 sentence.")
        }
    }

    fun buildActionPrompt(
        profile: PetProfile,
        notifications: List<NotificationItem>,
        userCommand: String
    ): String {
        val systemPrompt = buildSystemPrompt(profile)
        val notifJson = notifications.take(5).joinToString("\n") { n ->
            "- id=\"${n.id}\" [${n.appLabel}] ${n.title.orEmpty()}: ${n.text?.take(60).orEmpty()}"
        }
        return buildString {
            appendLine(systemPrompt)
            appendLine()
            appendLine("Available notifications:")
            appendLine(notifJson)
            appendLine()
            appendLine("User command: \"$userCommand\"")
            append("Respond with the action to take (dismiss/reply/open/snooze) and notification id. Format: ACTION id=\"...\" reply=\"...\" (only include reply for reply action)")
        }
    }
}
