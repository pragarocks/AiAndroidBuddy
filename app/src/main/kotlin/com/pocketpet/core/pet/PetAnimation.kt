package com.pocketpet.core.pet

import kotlinx.serialization.Serializable

@Serializable
data class FrameSize(val width: Int, val height: Int)

@Serializable
data class AnimationState(
    val row: Int,
    val frames: Int,
    val fps: Int
)

@Serializable
data class PetAnimationData(
    val name: String,
    val species: String,
    val author: String = "",
    val frameSize: FrameSize,
    val states: Map<String, AnimationState>
)
