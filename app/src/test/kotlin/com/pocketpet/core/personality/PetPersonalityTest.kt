package com.pocketpet.core.personality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetPersonalityTest {

    @Test
    fun `default profile has sensible values`() {
        val profile = PetProfile.DEFAULT
        assertNotNull(profile.name)
        assertNotNull(profile.speechStyle)
        assertTrue(profile.name.isNotBlank())
    }

    @Test
    fun `fromStats creates correct speech style for SNARK`() {
        val profile = PetProfile.fromStats("boba", "Boba", "blob", PetStat.SNARK, PetStat.PATIENCE)
        assertTrue(profile.speechStyle.contains("sarcastic"))
        assertTrue(profile.systemPromptSuffix.isNotBlank())
    }

    @Test
    fun `fromStats creates correct speech style for WISDOM`() {
        val profile = PetProfile.fromStats("boba", "Boba", "blob", PetStat.WISDOM, PetStat.CHAOS)
        assertTrue(profile.speechStyle.contains("thoughtful"))
    }

    @Test
    fun `fromStats creates correct speech style for CARE`() {
        val profile = PetProfile.fromStats("boba", "Boba", "blob", PetStat.CARE, PetStat.SNARK)
        assertTrue(profile.speechStyle.contains("warm"))
    }

    @Test
    fun `fromStats creates correct speech style for CHAOS`() {
        val profile = PetProfile.fromStats("boba", "Boba", "blob", PetStat.CHAOS, PetStat.PATIENCE)
        assertTrue(profile.speechStyle.contains("chaotic"))
    }

    @Test
    fun `fromStats creates correct speech style for PATIENCE`() {
        val profile = PetProfile.fromStats("boba", "Boba", "blob", PetStat.PATIENCE, PetStat.CHAOS)
        assertTrue(profile.speechStyle.contains("calm"))
    }

    @Test
    fun `all stats produce non-empty suffix`() {
        PetStat.entries.forEach { stat ->
            val dumpStat = PetStat.entries.first { it != stat }
            val profile = PetProfile.fromStats("boba", "Boba", "blob", stat, dumpStat)
            assertTrue("Suffix empty for $stat", profile.systemPromptSuffix.isNotBlank())
        }
    }
}
