package com.example.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

class EffectsManager {
    class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var color: Int, var size: Float, var maxLife: Float) {
        var life: Float = maxLife
        val isAlive: Boolean get() = life > 0f
        fun update(dt: Float) {
            x += vx * dt
            y += vy * dt
            vy += 400f * dt
            life -= dt
        }
    }

    class Floater(var x: Float, var y: Float, var text: String, var color: Int, var size: Float, var maxLife: Float) {
        var life: Float = maxLife
        val isAlive: Boolean get() = life > 0f
        fun update(dt: Float) {
            y -= 60f * dt
            life -= dt
        }
    }

    private val particles = ArrayList<Particle>()
    private val floaters = ArrayList<Floater>()
    private var shakeTimer: Float = 0f
    private var shakeIntensity: Float = 0f
    var shakeOffsetX: Float = 0f
        private set
    var shakeOffsetY: Float = 0f
        private set

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val floaterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(8f, 0f, 0f, Color.BLACK)
    }

    fun triggerScreenShake(duration: Float = 0.25f, intensity: Float = 18f) {
        shakeTimer = duration
        shakeIntensity = intensity
    }

    fun addHitSparks(x: Float, y: Float, isHeavy: Boolean = false) = synchronized(this) {
        val count = if (isHeavy) 28 else 14
        val colors = intArrayOf(Color.parseColor("#FFD700"), Color.parseColor("#FF4500"), Color.WHITE, Color.parseColor("#00E5FF"))
        for (i in 0 until count) {
            val angle = (Math.random() * Math.PI * 2).toFloat()
            val speed = (150f + Math.random() * (if (isHeavy) 550f else 350f)).toFloat()
            val vx = Math.cos(angle.toDouble()).toFloat() * speed
            val vy = Math.sin(angle.toDouble()).toFloat() * speed
            val size = (4f + Math.random() * (if (isHeavy) 12f else 6f)).toFloat()
            particles.add(Particle(x, y, vx, vy, colors.random(), size, 0.35f))
        }
    }

    fun addComboText(x: Float, y: Float, comboHits: Int) = synchronized(this) {
        val text = when {
            comboHits >= 8 -> "LEGENDARY! $comboHits HITS"
            comboHits >= 5 -> "INSANE! $comboHits HITS"
            comboHits >= 3 -> "GREAT! $comboHits HITS"
            else -> "$comboHits HIT COMBO"
        }
        val color = when {
            comboHits >= 8 -> Color.parseColor("#FF0054")
            comboHits >= 5 -> Color.parseColor("#FFB703")
            comboHits >= 3 -> Color.parseColor("#3A86FF")
            else -> Color.YELLOW
        }
        floaters.add(Floater(x, y, text, color, 42f, 0.8f))
    }

    fun addDamageText(x: Float, y: Float, damage: Float) = synchronized(this) {
        floaters.add(Floater(x, y - 20f, "-${damage.toInt()}", Color.RED, 36f, 0.6f))
    }

    fun update(dt: Float) {
        if (shakeTimer > 0f) {
            shakeTimer -= dt
            shakeOffsetX = ((Math.random() - 0.5) * 2 * shakeIntensity).toFloat()
            shakeOffsetY = ((Math.random() - 0.5) * 2 * shakeIntensity).toFloat()
            if (shakeTimer <= 0f) { shakeOffsetX = 0f; shakeOffsetY = 0f }
        }
        synchronized(this) {
            val pIter = particles.iterator()
            while (pIter.hasNext()) {
                val p = pIter.next()
                p.update(dt)
                if (!p.isAlive) pIter.remove()
            }
            val fIter = floaters.iterator()
            while (fIter.hasNext()) {
                val f = fIter.next()
                f.update(dt)
                if (!f.isAlive) fIter.remove()
            }
        }
    }

    fun draw(canvas: Canvas) = synchronized(this) {
        for (p in particles) {
            particlePaint.color = p.color
            particlePaint.alpha = ((p.life / p.maxLife) * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, p.size, particlePaint)
        }
        for (f in floaters) {
            floaterPaint.color = f.color
            floaterPaint.textSize = f.size
            floaterPaint.alpha = ((f.life / f.maxLife) * 255).toInt().coerceIn(0, 255)
            canvas.drawText(f.text, f.x, f.y, floaterPaint)
        }
    }

    fun clear() = synchronized(this) {
        particles.clear()
        floaters.clear()
        shakeTimer = 0f
        shakeOffsetX = 0f
        shakeOffsetY = 0f
    }
}
