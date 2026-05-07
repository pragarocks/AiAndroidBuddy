package com.pocketpet.core.personality

enum class PetStat {
    PATIENCE, SNARK, WISDOM, CHAOS, CARE
}

data class PetProfile(
    val petId: String = "boba",
    val name: String = "Boba",
    val species: String = "blob",
    val peakStat: PetStat = PetStat.CARE,
    val dumpStat: PetStat = PetStat.SNARK,
    val speechStyle: String = "short and playful",
    val systemPromptSuffix: String = ""
) {
    companion object {
        val DEFAULT = PetProfile()

        fun fromStats(
            petId: String,
            name: String,
            species: String,
            peakStat: PetStat,
            dumpStat: PetStat
        ): PetProfile {
            val (style, suffix) = personalityFor(peakStat, dumpStat)
            return PetProfile(petId, name, species, peakStat, dumpStat, style, suffix)
        }

        private fun personalityFor(peak: PetStat, dump: PetStat): Pair<String, String> = when (peak) {
            PetStat.PATIENCE -> "calm and reassuring" to "Take a deep breath. Everything is fine."
            PetStat.SNARK -> "sarcastic and witty" to "Wow, another notification. How exciting. (eye roll)"
            PetStat.WISDOM -> "thoughtful and insightful" to "Consider this carefully before acting."
            PetStat.CHAOS -> "chaotic and energetic" to "WHOA this is amazing let's do ALL THE THINGS!"
            PetStat.CARE -> "warm and encouraging" to "You're doing great! I've got your back."
        }
    }
}
