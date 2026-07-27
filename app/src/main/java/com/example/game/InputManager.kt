package com.example.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/**
 * Handles multi-touch input for a Floating Dynamic Joystick (Left thumb)
 * and Glass-morphic Action Buttons (Right thumb).
 */
class InputManager {

    // Floating Dynamic Joystick State
    var isJoystickActive: Boolean = false
    var joystickCenterX: Float = 0f
    var joystickCenterY: Float = 0f
    var joystickRadius: Float = 110f
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

    // Glass-morphic Paints
    private val glassBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glassBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val glowRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val joyBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30FFFFFF")
        style = Paint.Style.FILL
    }
    private val joyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val joyKnobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A038BDF8")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }

    fun layoutControls(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
        joystickRadius = height * 0.17f

        // Right Diamond Action Buttons area
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

        // Pause Button at top right
        btnPause.set(width - 120f, 25f, width - 25f, 85f)
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

                // Floating Dynamic Joystick: Touch down on left side of screen
                if (x < screenWidth * 0.5f && joystickPointerId == -1) {
                    joystickPointerId = pointerId
                    joystickCenterX = x
                    joystickCenterY = y
                    joystickKnobX = x
                    joystickKnobY = y
                    isJoystickActive = true
                    moveDirX = 0f
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
        // 1. Draw Floating Dynamic Joystick if touched/active
        if (isJoystickActive) {
            canvas.drawCircle(joystickCenterX, joystickCenterY, joystickRadius, joyBasePaint)
            canvas.drawCircle(joystickCenterX, joystickCenterY, joystickRadius, joyStrokePaint)
            canvas.drawCircle(joystickKnobX, joystickKnobY, joystickRadius * 0.45f, joyKnobPaint)
            canvas.drawCircle(joystickKnobX, joystickKnobY, joystickRadius * 0.45f, joyStrokePaint)
        }

        // 2. Draw Glass-morphic Action Buttons
        drawGlassButton(canvas, btnHeavy, "HEAVY", isHeavyPressed, Color.parseColor("#DC2626"))
        drawGlassButton(canvas, btnLight, "LIGHT", isLightPressed, Color.parseColor("#2563EB"))
        drawGlassButton(canvas, btnCombo, "COMBO", isComboPressed, Color.parseColor("#7C3AED"))
        drawGlassButton(canvas, btnRoll, "ROLL", isRollPressed, Color.parseColor("#059669"))
        drawGlassButton(canvas, btnBlock, "BLOCK", isBlockPressed, Color.parseColor("#D97706"))

        // 3. Draw Glass Pause Button
        drawGlassButton(canvas, btnPause, "PAUSE", isPausePressed, Color.parseColor("#4B5563"), isOval = false)
    }

    private fun drawGlassButton(
        canvas: Canvas,
        rect: RectF,
        label: String,
        isPressed: Boolean,
        accentColor: Int,
        isOval: Boolean = true
    ) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val radius = rect.width() / 2f

        // Glass-morphic Gradient Fill
        val baseColor1 = if (isPressed) accentColor else Color.parseColor("#40FFFFFF")
        val baseColor2 = if (isPressed) Color.parseColor("#D0000000") else Color.parseColor("#20000000")

        glassBgPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            baseColor1, baseColor2, Shader.TileMode.CLAMP
        )

        glassBorderPaint.color = if (isPressed) accentColor else Color.parseColor("#B0FFFFFF")

        if (isOval) {
            // Glow aura on press
            if (isPressed) {
                glowRingPaint.color = accentColor
                glowRingPaint.alpha = 180
                canvas.drawCircle(cx, cy, radius + 6f, glowRingPaint)
            }
            canvas.drawCircle(cx, cy, radius, glassBgPaint)
            canvas.drawCircle(cx, cy, radius, glassBorderPaint)
        } else {
            canvas.drawRoundRect(rect, 12f, 12f, glassBgPaint)
            canvas.drawRoundRect(rect, 12f, 12f, glassBorderPaint)
        }

        textPaint.textSize = rect.height() * 0.28f
        textPaint.color = Color.WHITE
        canvas.drawText(label, cx, cy + textPaint.textSize * 0.35f, textPaint)
    }
}
