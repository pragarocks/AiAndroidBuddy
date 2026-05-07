package com.pocketpet.services

import com.pocketpet.core.ai.IntentRecognizer
import com.pocketpet.core.ai.LlmPromptBuilder
import com.pocketpet.core.notifications.NotificationItem
import com.pocketpet.core.personality.PetProfile
import com.pocketpet.core.personality.PetStat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuickSummaryTileTest {

    private lateinit var promptBuilder: LlmPromptBuilder
    private lateinit var profile: PetProfile

    @Before
    fun setUp() {
        promptBuilder = LlmPromptBuilder()
        profile = PetProfile(
            petId = "boba",
            name = "Boba",
            species = "blob",
            peakStat = PetStat.CARE,
            dumpStat = PetStat.SNARK,
            speechStyle = "short and playful"
        )
    }

    @Test
    fun `summarise prompt built correctly for tile trigger`() {
        val notifications = listOf(
            NotificationItem("1", "com.whatsapp", "WhatsApp", "Alice", "Hey there!", System.currentTimeMillis(), 1, null),
            NotificationItem("2", "com.gmail", "Gmail", "Invoice due", "Your invoice is overdue", System.currentTimeMillis(), 2, "email")
        )
        val prompt = promptBuilder.buildSummarisePrompt(profile, notifications)
        assertTrue(prompt.contains("Boba"))
        assertTrue(prompt.contains("WhatsApp"))
        assertTrue(prompt.contains("Gmail"))
    }

    @Test
    fun `empty notifications handled gracefully`() {
        val notifications = emptyList<NotificationItem>()
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun `prompt stays within token budget for tile use case`() {
        val notifications = (1..10).map { i ->
            NotificationItem("$i", "app$i", "App $i", "Title $i", "A" .repeat(100), System.currentTimeMillis(), 0, null)
        }
        val prompt = promptBuilder.buildSummarisePrompt(profile, notifications)
        val estimatedTokens = prompt.split("\\s+".toRegex()).size
        assertTrue("Prompt should be under 800 tokens, was $estimatedTokens", estimatedTokens < 800)
    }
}
