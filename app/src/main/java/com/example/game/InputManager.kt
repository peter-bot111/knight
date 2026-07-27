package com.example.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Handles multi-touch input for Virtual Joystick (Left) and Action Buttons (Right)
 * drawn directly on the Canvas.
 */
class InputManager {

    // On-screen touch zones
    val joystickCenter = RectF()
    var joystickRadius: Float = 110f
    var joystickKnobX: Float = 0f
    var joystickKnobY: Float = 0f
    var joystickPointerId: Int = -1

    // Virtual Input States
    var moveDirX: Float = 0f
    var isJumpPressed: Boolean = false
    var isCrouchPressed: Boolean = false
    var isLightPressed: Boolean = false
    var isHeavyPressed: Boolean = false
    var isComboPressed: Boolean = false
    var isRollPressed: Boolean = false
    var isBlockPressed: Boolean = false
    var isPausePressed: Boolean = false

    // Action Button hitboxes
    val btnLight = RectF()
    val btnHeavy = RectF()
    val btnCombo = RectF()
    val btnRoll = RectF()
    val btnBlock = RectF()
    val btnPause = RectF()

    // Paints
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40FFFFFF")
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#803A86FF")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    fun layoutControls(width: Float, height: Float) {
        // Left Joystick area
        val joyX = width * 0.16f
        val joyY = height * 0.72f
        joystickRadius = height * 0.18f
        joystickCenter.set(joyX - joystickRadius, joyY - joystickRadius, joyX + joystickRadius, joyY + joystickRadius)
        joystickKnobX = joyX
        joystickKnobY = joyY

        // Right Diamond Action Buttons area
        val rx = width * 0.82f
        val ry = height * 0.72f
        val bSize = height * 0.12f

        // Top: HEAVY
        btnHeavy.set(rx - bSize, ry - bSize * 2.2f, rx + bSize, ry - bSize * 0.2f)
        // Left: LIGHT
        btnLight.set(rx - bSize * 2.2f, ry - bSize, rx - bSize * 0.2f, ry + bSize)
        // Right: COMBO
        btnCombo.set(rx + bSize * 0.2f, ry - bSize, rx + bSize * 2.2f, ry + bSize)
        // Bottom-Left: ROLL
        btnRoll.set(rx - bSize * 2.0f, ry + bSize * 0.4f, rx - bSize * 0.2f, ry + bSize * 2.0f)
        // Bottom-Right: BLOCK
        btnBlock.set(rx + bSize * 0.2f, ry + bSize * 0.4f, rx + bSize * 2.0f, ry + bSize * 2.0f)

        // Pause Button at top right
        btnPause.set(width - 100f, 25f, width - 25f, 90f)
    }

    fun handleTouchEvent(
        action: Int,
        pointerIndex: Int,
        pointerId: Int,
        x: Float,
        y: Float,
        f1: Fighter
    ) {
        when (action) {
            android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                // Check Pause Button
                if (btnPause.contains(x, y)) {
                    isPausePressed = true
                    return
                }

                // Check Joystick
                val dx = x - joystickCenter.centerX()
                val dy = y - joystickCenter.centerY()
                val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                if (dist <= joystickRadius * 1.5f && joystickPointerId == -1) {
                    joystickPointerId = pointerId
                    updateJoystick(dx, dy, dist, f1)
                }

                // Check Right Action Buttons
                if (btnLight.contains(x, y)) {
                    isLightPressed = true
                    f1.lightAttack()
                } else if (btnHeavy.contains(x, y)) {
                    isHeavyPressed = true
                    f1.heavyAttack()
                } else if (btnCombo.contains(x, y)) {
                    isComboPressed = true
                    f1.comboAttack()
                } else if (btnRoll.contains(x, y)) {
                    isRollPressed = true
                    f1.roll()
                } else if (btnBlock.contains(x, y)) {
                    isBlockPressed = true
                    f1.block(true)
                }
            }

            android.view.MotionEvent.ACTION_MOVE -> {
                if (pointerId == joystickPointerId) {
                    val dx = x - joystickCenter.centerX()
                    val dy = y - joystickCenter.centerY()
                    val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    updateJoystick(dx, dy, dist, f1)
                }
            }

            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_POINTER_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                if (pointerId == joystickPointerId) {
                    joystickPointerId = -1
                    joystickKnobX = joystickCenter.centerX()
                    joystickKnobY = joystickCenter.centerY()
                    moveDirX = 0f
                    f1.move(0f)
                    f1.crouch(false)
                }

                if (btnBlock.contains(x, y)) {
                    isBlockPressed = false
                    f1.block(false)
                }
            }
        }
    }

    private fun updateJoystick(dx: Float, dy: Float, dist: Float, f1: Fighter) {
        val maxR = joystickRadius
        val clampedDist = Math.min(dist, maxR)
        val angle = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()

        joystickKnobX = joystickCenter.centerX() + Math.cos(angle.toDouble()).toFloat() * clampedDist
        joystickKnobY = joystickCenter.centerY() + Math.sin(angle.toDouble()).toFloat() * clampedDist

        val normX = dx / maxR
        val normY = dy / maxR

        moveDirX = normX.coerceIn(-1f, 1f)
        f1.move(moveDirX)

        if (normY < -0.5f) {
            f1.jump()
        } else if (normY > 0.5f) {
            f1.crouch(true)
        } else {
            f1.crouch(false)
        }
    }

    fun drawOverlay(canvas: Canvas) {
        // Draw Joystick Base & Knob
        canvas.drawCircle(joystickCenter.centerX(), joystickCenter.centerY(), joystickRadius, basePaint)
        canvas.drawCircle(joystickCenter.centerX(), joystickCenter.centerY(), joystickRadius, strokePaint)
        canvas.drawCircle(joystickKnobX, joystickKnobY, joystickRadius * 0.42f, if (joystickPointerId != -1) highlightPaint else basePaint)
        canvas.drawCircle(joystickKnobX, joystickKnobY, joystickRadius * 0.42f, strokePaint)

        // Draw Action Buttons
        drawActionButton(canvas, btnHeavy, "HEAVY", isHeavyPressed, Color.parseColor("#DC2626"))
        drawActionButton(canvas, btnLight, "LIGHT", isLightPressed, Color.parseColor("#2563EB"))
        drawActionButton(canvas, btnCombo, "COMBO", isComboPressed, Color.parseColor("#7C3AED"))
        drawActionButton(canvas, btnRoll, "ROLL", isRollPressed, Color.parseColor("#059669"))
        drawActionButton(canvas, btnBlock, "BLOCK", isBlockPressed, Color.parseColor("#D97706"))

        // Draw Pause Button
        canvas.drawRoundRect(btnPause, 12f, 12f, basePaint)
        canvas.drawRoundRect(btnPause, 12f, 12f, strokePaint)
        textPaint.textSize = 24f
        canvas.drawText("⏸ PAUSE", btnPause.centerX(), btnPause.centerY() + 8f, textPaint)
    }

    private fun drawActionButton(
        canvas: Canvas,
        rect: RectF,
        label: String,
        isPressed: Boolean,
        accentColor: Int
    ) {
        basePaint.color = if (isPressed) accentColor else Color.parseColor("#50000000")
        canvas.drawOval(rect, basePaint)
        canvas.drawOval(rect, strokePaint)

        textPaint.textSize = rect.height() * 0.28f
        canvas.drawText(label, rect.centerX(), rect.centerY() + textPaint.textSize * 0.35f, textPaint)
    }
}
