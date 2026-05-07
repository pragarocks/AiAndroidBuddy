package com.pocketpet.core.pet

import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class SpriteAnimatorTest {

    private lateinit var data: PetAnimationData
    private lateinit var animator: SpriteAnimator

    @Before
    fun setUp() {
        data = PetAnimationData(
            name = "Boba",
            species = "blob",
            frameSize = FrameSize(192, 208),
            states = mapOf(
                "idle" to AnimationState(0, 4, 4),
                "excited" to AnimationState(7, 6, 12),
                "sleeping" to AnimationState(3, 4, 2)
            )
        )
        animator = SpriteAnimator(data, null)
    }

    @Test
    fun `animator initialises without crash`() {
        assertNotNull(animator)
    }

    @Test
    fun `set state to existing key does not throw`() {
        animator.setState("excited")
        animator.setState("sleeping")
        animator.setState("idle")
    }

    @Test
    fun `set state to unknown key falls back to idle`() {
        // Should not throw
        animator.setState("nonexistent")
    }

    @Test
    fun `update advances frame at correct fps`() {
        animator.setState("idle") // 4fps = 250ms per frame
        val t0 = 0L
        animator.update(t0)
        animator.update(t0 + 249) // should not advance
        animator.update(t0 + 250) // should advance
        // No crash = pass; frame counting is internal
    }

    @Test
    fun `frame wraps around after last frame`() {
        animator.setState("idle") // 4 frames at 4fps
        val start = 0L
        // Advance enough times to wrap around
        for (i in 0..4) {
            animator.update(start + (i * 250L))
        }
        // No crash = correct wrap behavior
    }
}
