package com.example.game

/**
 * Intelligent state-machine AI for Player 2 (KNIGHT RED) on Hard difficulty.
 */
class AIController {

    private var decisionTimer: Float = 0f
    private val decisionInterval: Float = 0.12f // Evaluates state every 120ms

    fun update(ai: Fighter, player1: Fighter, dt: Float) {
        if (ai.state == FighterState.DEATH || ai.state == FighterState.HIT_STUN) return

        decisionTimer += dt
        if (decisionTimer < decisionInterval) return
        decisionTimer = 0f

        val distance = Math.abs((ai.x + ai.width / 2f) - (player1.x + player1.width / 2f))
        val isP1Attacking = player1.isHitboxActive || player1.state.isAttacking

        // 1. Reactionary Defense
        if (isP1Attacking && distance < 260f) {
            val reactRoll = Math.random() < 0.70 // 70% chance to block or roll when under attack
            if (reactRoll) {
                if (Math.random() < 0.50) {
                    ai.roll()
                } else {
                    ai.block(true)
                }
                return
            }
        } else if (ai.state == FighterState.BLOCKING) {
            ai.block(false)
        }

        // 2. Punish Whiff / Recovery
        if (player1.state.isAttacking && player1.stateTimer > 0.22f && distance <= 220f) {
            if (Math.random() < 0.85) {
                if (Math.random() < 0.5) ai.comboAttack() else ai.heavyAttack()
                return
            }
        }

        // 3. Low Health Retreat or Desperation Roll
        if (ai.health < 25f && distance < 140f && Math.random() < 0.40) {
            ai.roll()
            return
        }

        // 4. Spacing & Movement
        if (distance > 340f) {
            // Far range -> Close in fast
            val moveDir = if (player1.x < ai.x) -1f else 1f
            ai.move(moveDir)

            // Occasional jump or roll close-in
            if (Math.random() < 0.15 && ai.state.canMove) {
                ai.jump()
            }
        } else if (distance in 180f..340f) {
            // Medium range -> Approach steadily
            val moveDir = if (player1.x < ai.x) -0.8f else 0.8f
            ai.move(moveDir)
        } else {
            // Close Range Combat Aggression Mixups
            ai.move(0f)
            val attackChoice = Math.random()
            when {
                attackChoice < 0.40 -> ai.lightAttack()
                attackChoice < 0.70 -> ai.heavyAttack()
                attackChoice < 0.92 -> ai.comboAttack()
                else -> ai.roll()
            }
        }
    }
}
