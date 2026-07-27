package com.example.game

/**
 * Enumeration of all possible states a Fighter can be in.
 */
enum class FighterState {
    IDLE,
    RUN,
    JUMP,
    FALL,
    CROUCH,
    LIGHT_ATTACK,
    HEAVY_ATTACK,
    COMBO_ATTACK,
    CROUCH_ATTACK,
    ROLL,
    BLOCKING,
    HIT_STUN,
    DEATH;

    val isAttacking: Boolean
        get() = this == LIGHT_ATTACK || this == HEAVY_ATTACK || this == COMBO_ATTACK || this == CROUCH_ATTACK

    val isInvulnerable: Boolean
        get() = this == ROLL

    val canMove: Boolean
        get() = this == IDLE || this == RUN || this == JUMP || this == FALL || this == CROUCH

    val canAttack: Boolean
        get() = this == IDLE || this == RUN || this == JUMP || this == FALL || this == CROUCH
}
