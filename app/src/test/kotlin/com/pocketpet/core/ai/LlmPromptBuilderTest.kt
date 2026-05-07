package com.pocketpet.core.ai

import com.pocketpet.core.notifications.NotificationItem
import com.pocketpet.core.personality.PetProfile
import com.pocketpet.core.personality.PetStat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LlmPromptBuilderTest {

    private lateinit var builder: LlmPromptBuilder
    private lateinit var profile: PetProfile

    @Before
    fun setUp() {
        builder = LlmPromptBuilder()
        profile = PetProfile(
            petId = "boba",
            name = "Boba",
            species = "blob",
            peakStat = PetStat.CARE,
            dumpStat = PetStat.SNARK,
            speechStyle = "short and playful",
            systemPromptSuffix = "Be extra warm."
        )
    }

    @Test
    fun `system prompt contains pet name and species`() {
        val prompt = builder.buildSystemPrompt(profile)
        assertTrue(prompt.contains("Boba"))
        assertTrue(prompt.contains("blob"))
    }

    @Test
    fun `system prompt contains speech style`() {
        val prompt = builder.buildSystemPrompt(profile)
        assertTrue(prompt.contains("short and playful"))
    }

    @Test
    fun `system prompt contains peak stat`() {
        val prompt = builder.buildSystemPrompt(profile)
        assertTrue(prompt.contains("CARE"))
    }

    @Test
    fun `system prompt includes suffix when present`() {
        val prompt = builder.buildSystemPrompt(profile)
        assertTrue(prompt.contains("Be extra warm."))
    }

    @Test
    fun `system prompt does not exceed 300 tokens roughly`() {
        val prompt = builder.buildSystemPrompt(profile)
        val wordCount = prompt.split("\\s+".toRegex()).size
        assertTrue("System prompt too long: $wordCount words", wordCount < 300)
    }

    @Test
    fun `summarise prompt contains notification content`() {
        val notifications = listOf(
            NotificationItem("1", "com.whatsapp", "WhatsApp", "Alice", "Are you coming?", System.currentTimeMillis(), 1, null),
            NotificationItem("2", "com.gmail", "Gmail", "Meeting at 3pm", "Don't forget the call", System.currentTimeMillis(), 2, "email")
        )
        val prompt = builder.buildSummarisePrompt(profile, notifications)
        assertTrue(prompt.contains("WhatsApp"))
        assertTrue(prompt.contains("Gmail"))
        assertTrue(prompt.contains("Boba"))
    }

    @Test
    fun `summarise prompt limits to 10 notifications`() {
        val notifications = (1..20).map { i ->
            NotificationItem("$i", "pkg$i", "App$i", "Title$i", "Text$i", System.currentTimeMillis(), 0, null)
        }
        val prompt = builder.buildSummarisePrompt(profile, notifications)
        assertFalse(prompt.contains("App11"))
        assertFalse(prompt.contains("App20"))
    }

    @Test
    fun `reply prompt includes notification content and user request`() {
        val notif = NotificationItem("1", "com.whatsapp", "WhatsApp", "Alice", "Are you coming?", System.currentTimeMillis(), 1, null)
        val prompt = builder.buildReplyPrompt(profile, notif, "say yes")
        assertTrue(prompt.contains("WhatsApp"))
        assertTrue(prompt.contains("Are you coming?"))
        assertTrue(prompt.contains("say yes"))
    }

    @Test
    fun `action prompt includes notification ids`() {
        val notifications = listOf(
            NotificationItem("key123", "com.whatsapp", "WhatsApp", "Alice", "Hey!", System.currentTimeMillis(), 1, null)
        )
        val prompt = builder.buildActionPrompt(profile, notifications, "dismiss the WhatsApp one")
        assertTrue(prompt.contains("key123"))
        assertTrue(prompt.contains("dismiss the WhatsApp one"))
    }
}
