package com.pocketpet.core.nudge

/** Types of health/wellness nudges the pet can give */
enum class NudgeType {
    WATER, POSTURE, EYES, SCREEN_TIME, STEP, SLEEP, APP_TIME
}

data class NudgeMessage(
    val type: NudgeType,
    val text: String,
    val emoji: String
)

/** Hardcoded pool of clever nudge messages, randomly selected at runtime */
object NudgeMessages {

    private val water = listOf(
        NudgeMessage(NudgeType.WATER, "Hey! Drink some water. I'm thirsty just looking at you.", "💧"),
        NudgeMessage(NudgeType.WATER, "Water time! Your body is 60% water... don't let it drop!", "🥤"),
        NudgeMessage(NudgeType.WATER, "Hydration check! When did you last drink water? Exactly.", "💦"),
        NudgeMessage(NudgeType.WATER, "Your cells are literally begging for water right now.", "🌊"),
        NudgeMessage(NudgeType.WATER, "Water makes your brain work better. Drink up, smarty! 🧠", "💧")
    )

    private val posture = listOf(
        NudgeMessage(NudgeType.POSTURE, "Sit up straight! Your spine called, it's not happy.", "🧍"),
        NudgeMessage(NudgeType.POSTURE, "Roll those shoulders back. There you go! Much better.", "💪"),
        NudgeMessage(NudgeType.POSTURE, "Chin up! Literally. You're hunching over your phone.", "😤"),
        NudgeMessage(NudgeType.POSTURE, "Future-you will thank you for sitting up right now.", "🪑"),
        NudgeMessage(NudgeType.POSTURE, "Uncross those legs. Circulation matters! Stand if you can.", "🦵")
    )

    private val eyes = listOf(
        NudgeMessage(NudgeType.EYES, "20-20-20 rule! Look 20ft away for 20 seconds. Starting now.", "👁️"),
        NudgeMessage(NudgeType.EYES, "Blink! Studies show we blink way less when staring at screens.", "😑"),
        NudgeMessage(NudgeType.EYES, "Give your eyes a break. Look at something far away for a bit.", "🌅"),
        NudgeMessage(NudgeType.EYES, "Your eyes are tired. Close them for 20 seconds. I'll wait.", "😴")
    )

    private val step = listOf(
        NudgeMessage(NudgeType.STEP, "You've been still for a while. A 2-minute walk does wonders!", "🚶"),
        NudgeMessage(NudgeType.STEP, "Stand up and stretch! Your body will thank you.", "🧘"),
        NudgeMessage(NudgeType.STEP, "Movement break! March in place for 30 seconds. Yes, really.", "🏃"),
        NudgeMessage(NudgeType.STEP, "Quick stretch! Arms up, wiggle your fingers. Feels good, right?", "✋")
    )

    private val sleep = listOf(
        NudgeMessage(NudgeType.SLEEP, "It's getting late! Even I need beauty sleep (sort of).", "😴"),
        NudgeMessage(NudgeType.SLEEP, "Good sleep = better brain tomorrow. Wind down soon!", "🌙"),
        NudgeMessage(NudgeType.SLEEP, "Screens at night hurt your sleep. Maybe wrap up soon?", "⭐"),
        NudgeMessage(NudgeType.SLEEP, "Your future self at 7am will love you for sleeping now.", "🌛")
    )

    private val screenTime = listOf(
        NudgeMessage(NudgeType.SCREEN_TIME, "You've been on your phone a while. I'm a little worried.", "📱"),
        NudgeMessage(NudgeType.SCREEN_TIME, "Screen time check! How are you feeling? Take a real break.", "⏰"),
        NudgeMessage(NudgeType.SCREEN_TIME, "I love hanging out with you, but maybe look up for a bit?", "🌿"),
        NudgeMessage(NudgeType.SCREEN_TIME, "Touch grass. I'll be here when you get back. I promise.", "☀️")
    )

    private val appTime = listOf(
        NudgeMessage(NudgeType.APP_TIME, "You've been in this app for a while! Maybe time for a break?", "⏱️"),
        NudgeMessage(NudgeType.APP_TIME, "Still here? That's okay, but don't forget to look up sometimes!", "👀"),
        NudgeMessage(NudgeType.APP_TIME, "Long session! Your eyes and brain could use a short rest.", "🧠"),
        NudgeMessage(NudgeType.APP_TIME, "Hey! Step away for 5 minutes, stretch, then come back refreshed!", "🚶")
    )

    fun random(type: NudgeType): NudgeMessage = when (type) {
        NudgeType.WATER       -> water.random()
        NudgeType.POSTURE     -> posture.random()
        NudgeType.EYES        -> eyes.random()
        NudgeType.STEP        -> step.random()
        NudgeType.SLEEP       -> sleep.random()
        NudgeType.SCREEN_TIME -> screenTime.random()
        NudgeType.APP_TIME    -> appTime.random()
    }

    /** Pick a nudge type appropriate for the current hour */
    fun typeForHour(hour: Int): NudgeType = when {
        hour in 23..23 || hour in 0..1 -> NudgeType.SLEEP
        hour % 3 == 0                  -> NudgeType.WATER
        hour % 3 == 1                  -> NudgeType.POSTURE
        else                           -> NudgeType.EYES
    }
}
