package com.pocketpet.core.pet

sealed interface PetState {
    data object Idle : PetState
    data object Excited : PetState
    data object Working : PetState
    data object Thinking : PetState
    data object Success : PetState
    data object Error : PetState
    data object Waiting : PetState
    data object Sleeping : PetState
    data object Running : PetState

    val animationKey: String
        get() = when (this) {
            is Idle -> "idle"
            is Excited -> "excited"
            is Working -> "working"
            is Thinking -> "thinking"
            is Success -> "success"
            is Error -> "error"
            is Waiting -> "waiting"
            is Sleeping -> "sleeping"
            is Running -> "running"
        }
}
