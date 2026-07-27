package com.example.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Handles multi-touch input for a Fixed AAA Virtual Thumbstick (Left thumb),
 * Glass-morphic Action Buttons (Right thumb), and top-right Pause Button.
 */
class InputManager {

    // Fixed Virtual Joystick State
    var isJoystickActive: Boolean = false
    var joystickCenterX: Float = 0f
    var joystickCenterY: Float = 0f
    var joystickRadius: Float = 120f
    var joystickKnobX: Float = 0f
    var joystickKnobY: Float = 0f
    var joystickPointerId: Int = -1

    // Virtual Input States
    var moveDirX: Float = 0f
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

    // View dimensions
    private var screenWidth: Float = 1920f
    private var screenHeight: Float = 1080f

    // Pre-allocated Glass-morphic Paints
    private val glassBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#350F172A")
    }
    private val glassBgPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glassBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = Color.parseColor("#B0FFFFFF")
    }
    private val glowRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    // Joystick Paints
    private val joyBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#450F172A")
        style = Paint.Style.FILL
    }
    private val joyBaseRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9038BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }
    private val joyInnerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val joyKnobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D038BDF8")
        style = Paint.Style.FILL
    }
    private val joyKnobBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    // Text & Icon Paints
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }
    private val pauseIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    fun layoutControls(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height

        // 1. Fixed Joystick at Bottom-Left Screen Position
        joystickRadius = height * 0.18f
        joystickCenterX = width * 0.16f
        joystickCenterY = height * 0.70f
        joystickKnobX = joystickCenterX
        joystickKnobY = joystickCenterY

        // 2. Right Diamond Action Buttons area
        val rx = width * 0.82f
        val ry = height * 0.70f
        val bSize = height * 0.115f

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

        // 3. Circular Pause Button in Absolute Top-Right Corner
        val pRadius = 32f
        val pCx = width - 65f
        val pCy = 55f
        btnPause.set(pCx - pRadius, pCy - pRadius, pCx + pRadius, pCy + pRadius)
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

                // Check Right Action Buttons
                if (btnLight.contains(x, y)) {
                    isLightPressed = true
                    f1.lightAttack()
                    return
                } else if (btnHeavy.contains(x, y)) {
                    isHeavyPressed = true
                    f1.heavyAttack()
                    return
                } else if (btnCombo.contains(x, y)) {
                    isComboPressed = true
                    f1.comboAttack()
                    return
                } else if (btnRoll.contains(x, y)) {
                    isRollPressed = true
                    f1.roll()
                    return
                } else if (btnBlock.contains(x, y)) {
                    isBlockPressed = true
                    f1.block(true)
                    return
                }

                // Fixed Joystick: Touch down on left side of screen
                if (x < screenWidth * 0.48f && joystickPointerId == -1) {
                    joystickPointerId = pointerId
                    isJoystickActive = true
                    val dx = x - joystickCenterX
                    val dy = y - joystickCenterY
                    val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    updateJoystick(dx, dy, dist, f1)
                }
            }

            android.view.MotionEvent.ACTION_MOVE -> {
                if (pointerId == joystickPointerId && isJoystickActive) {
                    val dx = x - joystickCenterX
                    val dy = y - joystickCenterY
                    val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    updateJoystick(dx, dy, dist, f1)
                }
            }

            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_POINTER_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                if (pointerId == joystickPointerId) {
                    joystickPointerId = -1
                    isJoystickActive = false
                    // Immediate snap-back to center
                    joystickKnobX = joystickCenterX
                    joystickKnobY = joystickCenterY
                    moveDirX = 0f
                    f1.move(0f)
                    f1.crouch(false)
                }

                if (btnLight.contains(x, y)) isLightPressed = false
                if (btnHeavy.contains(x, y)) isHeavyPressed = false
                if (btnCombo.contains(x, y)) isComboPressed = false
                if (btnRoll.contains(x, y)) isRollPressed = false
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

        joystickKnobX = joystickCenterX + Math.cos(angle.toDouble()).toFloat() * clampedDist
        joystickKnobY = joystickCenterY + Math.sin(angle.toDouble()).toFloat() * clampedDist

        val normX = dx / maxR
        val normY = dy / maxR

        moveDirX = normX.coerceIn(-1f, 1f)
        f1.move(moveDirX)

        if (normY < -0.45f) {
            f1.jump()
        } else if (normY > 0.45f) {
            f1.crouch(true)
        } else {
            f1.crouch(false)
        }
    }

    fun drawOverlay(canvas: Canvas) {
        // 1. Draw Permanent Fixed Virtual Thumbstick Base & Knob
        canvas.drawCircle(joystickCenterX, joystickCenterY, joystickRadius, joyBasePaint)
        canvas.drawCircle(joystickCenterX, joystickCenterY, joystickRadius, joyBaseRingPaint)
        canvas.drawCircle(joystickCenterX, joystickCenterY, joystickRadius * 0.5f, joyInnerRingPaint)

        // Inner Thumbstick Knob
        val knobRadius = joystickRadius * 0.42f
        canvas.drawCircle(joystickKnobX, joystickKnobY, knobRadius, joyKnobPaint)
        canvas.drawCircle(joystickKnobX, joystickKnobY, knobRadius, joyKnobBorderPaint)

        // 2. Draw Glass-morphic Action Buttons
        drawGlassButton(canvas, btnHeavy, "HEAVY", isHeavyPressed, Color.parseColor("#DC2626"))
        drawGlassButton(canvas, btnLight, "LIGHT", isLightPressed, Color.parseColor("#2563EB"))
        drawGlassButton(canvas, btnCombo, "COMBO", isComboPressed, Color.parseColor("#7C3AED"))
        drawGlassButton(canvas, btnRoll, "ROLL", isRollPressed, Color.parseColor("#059669"))
        drawGlassButton(canvas, btnBlock, "BLOCK", isBlockPressed, Color.parseColor("#D97706"))

        // 3. Draw Polished Top-Right Pause Button
        drawPauseButton(canvas)
    }

    private fun drawGlassButton(
        canvas: Canvas,
        rect: RectF,
        label: String,
        isPressed: Boolean,
        accentColor: Int
    ) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val radius = rect.width() / 2f

        if (isPressed) {
            glowRingPaint.color = accentColor
            glowRingPaint.alpha = 200
            canvas.drawCircle(cx, cy, radius + 6f, glowRingPaint)

            glassBgPressedPaint.color = accentColor
            glassBgPressedPaint.alpha = 210
            canvas.drawCircle(cx, cy, radius, glassBgPressedPaint)
            glassBorderPaint.color = Color.WHITE
        } else {
            canvas.drawCircle(cx, cy, radius, glassBgPaint)
            glassBorderPaint.color = Color.parseColor("#B0FFFFFF")
        }

        canvas.drawCircle(cx, cy, radius, glassBorderPaint)

        textPaint.textSize = rect.height() * 0.28f
        textPaint.color = Color.WHITE
        canvas.drawText(label, cx, cy + textPaint.textSize * 0.35f, textPaint)
    }

    private fun drawPauseButton(canvas: Canvas) {
        val cx = btnPause.centerX()
        val cy = btnPause.centerY()
        val radius = btnPause.width() / 2f

        if (isPausePressed) {
            glassBgPressedPaint.color = Color.parseColor("#4B5563")
            glassBgPressedPaint.alpha = 220
            canvas.drawCircle(cx, cy, radius, glassBgPressedPaint)
            glassBorderPaint.color = Color.WHITE
        } else {
            canvas.drawCircle(cx, cy, radius, glassBgPaint)
            glassBorderPaint.color = Color.parseColor("#B0FFFFFF")
        }

        canvas.drawCircle(cx, cy, radius, glassBorderPaint)

        // Draw classic "||" pause bars icon
        val barW = 6f
        val barH = 20f
        val gap = 6f
        canvas.drawRoundRect(cx - gap - barW, cy - barH / 2f, cx - gap, cy + barH / 2f, 3f, 3f, pauseIconPaint)
        canvas.drawRoundRect(cx + gap, cy - barH / 2f, cx + gap + barW, cy + barH / 2f, 3f, 3f, pauseIconPaint)
    }
}
