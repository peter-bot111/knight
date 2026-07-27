package com.example.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Represents a playable or AI-controlled Knight fighter in the 2D arena.
 */
class Fighter(
    val isPlayer1: Boolean,
    var x: Float,
    var y: Float,
    var groundY: Float
) {
    // Fighter dimensions
    var width: Float = 220f
    var height: Float = 280f

    // Physics
    var vx: Float = 0f
    var vy: Float = 0f
    val speed: Float = 460f
    val jumpVelocity: Float = -920f
    val gravity: Float = 2300f
    val dashSpeed: Float = 850f

    // Orientation & State
    var facingLeft: Boolean = !isPlayer1
    var state: FighterState = FighterState.IDLE
        private set

    // Health & Combat Attributes
    val maxHealth: Float = 100f
    var health: Float = maxHealth
    var displayHealth: Float = maxHealth // Smooth trailing visual health
    var wins: Int = 0

    // Hitboxes & Hurtboxes
    val hurtbox: RectF = RectF()
    val hitbox: RectF = RectF()
    var isHitboxActive: Boolean = false

    // Timers & Counters
    var stateTimer: Float = 0f
    var hitStunTimer: Float = 0f
    var attackCooldown: Float = 0f
    var comboHits: Int = 0
    var comboWindowTimer: Float = 0f
    var lastHitDamage: Float = 0f

    // Animation framing
    var currentFrameIndex: Int = 0
    private var frameTimeCounter: Float = 0f
    private val frameDuration: Float = 0.08f // 80ms per frame

    // Color debug paint
    private val debugHurtboxPaint = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 3f }
    private val debugHitboxPaint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 4f }

    init {
        updateBoxes()
    }

    fun resetForRound(startX: Float) {
        x = startX
        y = groundY - height
        vx = 0f
        vy = 0f
        health = maxHealth
        displayHealth = maxHealth
        facingLeft = !isPlayer1
        state = FighterState.IDLE
        stateTimer = 0f
        hitStunTimer = 0f
        attackCooldown = 0f
        comboHits = 0
        comboWindowTimer = 0f
        isHitboxActive = false
        updateBoxes()
    }

    fun update(dt: Float, screenWidth: Float, opponent: Fighter) {
        // Smooth trailing health animation
        if (displayHealth > health) {
            displayHealth -= (displayHealth - health) * 5f * dt
            if (displayHealth < health) displayHealth = health
        }

        // Combo window decay
        if (comboHits > 0) {
            comboWindowTimer -= dt
            if (comboWindowTimer <= 0f) {
                comboHits = 0
            }
        }

        // Attack cooldown decay
        if (attackCooldown > 0f) {
            attackCooldown -= dt
        }

        // State Machine logic update
        when (state) {
            FighterState.HIT_STUN -> {
                hitStunTimer -= dt
                vx *= 0.88f // Friction during knockback
                if (hitStunTimer <= 0f) {
                    if (health <= 0f) {
                        setState(FighterState.DEATH)
                    } else {
                        setState(FighterState.IDLE)
                    }
                }
            }
            FighterState.LIGHT_ATTACK -> {
                stateTimer += dt
                // Activate hitbox during middle frames of attack
                isHitboxActive = stateTimer in 0.08f..0.22f
                if (stateTimer >= 0.32f) {
                    setState(FighterState.IDLE)
                }
            }
            FighterState.HEAVY_ATTACK -> {
                stateTimer += dt
                isHitboxActive = stateTimer in 0.12f..0.32f
                if (stateTimer >= 0.48f) {
                    setState(FighterState.IDLE)
                }
            }
            FighterState.COMBO_ATTACK -> {
                stateTimer += dt
                isHitboxActive = stateTimer in 0.06f..0.45f
                if (stateTimer >= 0.60f) {
                    setState(FighterState.IDLE)
                }
            }
            FighterState.CROUCH_ATTACK -> {
                stateTimer += dt
                isHitboxActive = stateTimer in 0.08f..0.24f
                if (stateTimer >= 0.36f) {
                    setState(FighterState.CROUCH)
                }
            }
            FighterState.ROLL -> {
                stateTimer += dt
                vx = if (facingLeft) -dashSpeed else dashSpeed
                if (stateTimer >= 0.38f) {
                    vx = 0f
                    setState(FighterState.IDLE)
                }
            }
            FighterState.DEATH -> {
                vx = 0f
            }
            else -> {
                // Auto face opponent when movable
                if (state.canMove && state != FighterState.CROUCH) {
                    facingLeft = x > opponent.x
                }
            }
        }

        // Apply Physics & Gravity
        vy += gravity * dt
        x += vx * dt
        y += vy * dt

        // Ground Collision
        if (y >= groundY - height) {
            y = groundY - height
            vy = 0f
            if (state == FighterState.JUMP || state == FighterState.FALL) {
                setState(FighterState.IDLE)
            }
        } else if (vy > 0f && state.canMove) {
            state = FighterState.FALL
        }

        // Screen Boundary Constraints
        val margin = 20f
        if (x < margin) x = margin
        if (x > screenWidth - width - margin) x = screenWidth - width - margin

        // Animation frame progression
        frameTimeCounter += dt
        if (frameTimeCounter >= frameDuration) {
            frameTimeCounter -= frameDuration
            currentFrameIndex++
        }

        updateBoxes()
    }

    /**
     * Prevents fighters from clipping through each other (Physical Presence).
     */
    fun resolveCollisionWith(other: Fighter) {
        val overlapX = (x + width / 2f) - (other.x + other.width / 2f)
        val minDistance = (width + other.width) * 0.42f

        if (Math.abs(overlapX) < minDistance) {
            val push = (minDistance - Math.abs(overlapX)) * 0.5f
            if (overlapX > 0) {
                x += push
                other.x -= push
            } else {
                x -= push
                other.x += push
            }
        }
    }

    fun setState(newState: FighterState) {
        if (state == FighterState.DEATH) return // Cannot exit death state
        if (state == newState) return

        state = newState
        stateTimer = 0f
        currentFrameIndex = 0
        frameTimeCounter = 0f

        if (!newState.isAttacking) {
            isHitboxActive = false
        }
    }

    // --- Action Inputs ---

    fun move(dirX: Float) {
        if (!state.canMove) return

        if (dirX < -0.2f) {
            vx = -speed
            if (y >= groundY - height) setState(FighterState.RUN)
        } else if (dirX > 0.2f) {
            vx = speed
            if (y >= groundY - height) setState(FighterState.RUN)
        } else {
            vx = 0f
            if (y >= groundY - height && state == FighterState.RUN) {
                setState(FighterState.IDLE)
            }
        }
    }

    fun jump() {
        if (y >= groundY - height && state.canMove) {
            vy = jumpVelocity
            setState(FighterState.JUMP)
        }
    }

    fun crouch(isCrouching: Boolean) {
        if (state == FighterState.IDLE || state == FighterState.RUN || state == FighterState.CROUCH) {
            if (isCrouching) {
                vx = 0f
                setState(FighterState.CROUCH)
            } else if (state == FighterState.CROUCH) {
                setState(FighterState.IDLE)
            }
        }
    }

    fun lightAttack() {
        if (state.canAttack && attackCooldown <= 0f) {
            vx = if (facingLeft) -80f else 80f
            setState(if (state == FighterState.CROUCH) FighterState.CROUCH_ATTACK else FighterState.LIGHT_ATTACK)
            attackCooldown = 0.2f
        }
    }

    fun heavyAttack() {
        if (state.canAttack && attackCooldown <= 0f) {
            vx = if (facingLeft) -120f else 120f
            setState(FighterState.HEAVY_ATTACK)
            attackCooldown = 0.35f
        }
    }

    fun comboAttack() {
        if (state.canAttack && attackCooldown <= 0f) {
            vx = if (facingLeft) -150f else 150f
            setState(FighterState.COMBO_ATTACK)
            attackCooldown = 0.5f
        }
    }

    fun roll() {
        if (state.canMove) {
            setState(FighterState.ROLL)
        }
    }

    fun block(isBlocking: Boolean) {
        if (state == FighterState.IDLE || state == FighterState.RUN || state == FighterState.BLOCKING) {
            if (isBlocking) {
                vx = 0f
                setState(FighterState.BLOCKING)
            } else if (state == FighterState.BLOCKING) {
                setState(FighterState.IDLE)
            }
        }
    }

    fun takeDamage(damage: Float, knockbackX: Float, stunTime: Float): Boolean {
        if (state.isInvulnerable || state == FighterState.DEATH) return false

        val actualDamage = if (state == FighterState.BLOCKING) damage * 0.15f else damage
        health -= actualDamage
        if (health < 0f) health = 0f
        lastHitDamage = actualDamage

        // Apply knockback
        vx = knockbackX

        if (state != FighterState.BLOCKING) {
            hitStunTimer = stunTime
            setState(FighterState.HIT_STUN)
        }

        return true
    }

    private fun updateBoxes() {
        // Hurtbox covers character body
        val crouchOffset = if (state == FighterState.CROUCH || state == FighterState.CROUCH_ATTACK) height * 0.35f else 0f
        hurtbox.set(
            x + width * 0.2f,
            y + crouchOffset + height * 0.08f,
            x + width * 0.8f,
            y + height
        )

        // Weapon Hitbox positioning flipped based on facing direction
        if (isHitboxActive) {
            val reach = when (state) {
                FighterState.HEAVY_ATTACK -> 180f
                FighterState.COMBO_ATTACK -> 210f
                else -> 130f
            }

            if (facingLeft) {
                hitbox.set(
                    x - reach + width * 0.2f,
                    y + height * 0.15f,
                    x + width * 0.35f,
                    y + height * 0.85f
                )
            } else {
                hitbox.set(
                    x + width * 0.65f,
                    y + height * 0.15f,
                    x + width + reach - width * 0.2f,
                    y + height * 0.85f
                )
            }
        } else {
            hitbox.setEmpty()
        }
    }

    fun drawDebugBoxes(canvas: Canvas) {
        canvas.drawRect(hurtbox, debugHurtboxPaint)
        if (isHitboxActive) {
            canvas.drawRect(hitbox, debugHitboxPaint)
        }
    }
}
