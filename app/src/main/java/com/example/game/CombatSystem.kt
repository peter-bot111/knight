package com.example.game

import android.graphics.RectF

/**
 * Evaluates hit collisions between fighters, applies damage, knockback, screen shake,
 * and updates combo mechanics.
 */
class CombatSystem(private val effectsManager: EffectsManager) {

    // Track hit registered per attack instance so a single swing hits only once
    private var p1HitAlready: Boolean = false
    private var p2HitAlready: Boolean = false

    fun update(p1: Fighter, p2: Fighter) {
        // Physical collision resolution (Pushing each other back)
        p1.resolveCollisionWith(p2)

        // Reset hit flags when attack finishes
        if (!p1.isHitboxActive) p1HitAlready = false
        if (!p2.isHitboxActive) p2HitAlready = false

        // Check P1 attacks P2
        if (p1.isHitboxActive && !p1HitAlready) {
            if (RectF.intersects(p1.hitbox, p2.hurtbox)) {
                if (processHit(attacker = p1, defender = p2)) {
                    p1HitAlready = true
                }
            }
        }

        // Check P2 attacks P1
        if (p2.isHitboxActive && !p2HitAlready) {
            if (RectF.intersects(p2.hitbox, p1.hurtbox)) {
                if (processHit(attacker = p2, defender = p1)) {
                    p2HitAlready = true
                }
            }
        }
    }

    private fun processHit(attacker: Fighter, defender: Fighter): Boolean {
        if (defender.state.isInvulnerable || defender.state == FighterState.DEATH) return false

        val baseDamage: Float
        val knockbackForce: Float
        val stunDuration: Float
        val isHeavy: Boolean

        when (attacker.state) {
            FighterState.HEAVY_ATTACK -> {
                baseDamage = 22f
                knockbackForce = 520f
                stunDuration = 0.40f
                isHeavy = true
            }
            FighterState.COMBO_ATTACK -> {
                baseDamage = 16f
                knockbackForce = 380f
                stunDuration = 0.32f
                isHeavy = false
            }
            FighterState.CROUCH_ATTACK -> {
                baseDamage = 9f
                knockbackForce = 200f
                stunDuration = 0.20f
                isHeavy = false
            }
            else -> { // LIGHT_ATTACK
                baseDamage = 11f
                knockbackForce = 260f
                stunDuration = 0.24f
                isHeavy = false
            }
        }

        // Direction of knockback
        val knockbackDir = if (attacker.x < defender.x) knockbackForce else -knockbackForce

        // Apply hit to defender
        val hitSuccess = defender.takeDamage(baseDamage, knockbackDir, stunDuration)

        if (hitSuccess) {
            // Update Combo Counter
            attacker.comboHits++
            attacker.comboWindowTimer = 1.6f

            val hitX = (attacker.hitbox.centerX() + defender.hurtbox.centerX()) / 2f
            val hitY = (attacker.hitbox.centerY() + defender.hurtbox.centerY()) / 2f

            // Visual and Camera Feedback
            effectsManager.addHitSparks(hitX, hitY, isHeavy)
            effectsManager.addDamageText(defender.x + defender.width / 2f, defender.y, defender.lastHitDamage)

            if (attacker.comboHits >= 2) {
                effectsManager.addComboText(attacker.x, attacker.y - 40f, attacker.comboHits)
            }

            if (isHeavy) {
                effectsManager.triggerScreenShake(duration = 0.30f, intensity = 22f)
            } else {
                effectsManager.triggerScreenShake(duration = 0.12f, intensity = 8f)
            }
        }

        return hitSuccess
    }

    fun reset() {
        p1HitAlready = false
        p2HitAlready = false
    }
}
