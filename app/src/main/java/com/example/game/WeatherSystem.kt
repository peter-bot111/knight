package com.example.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

enum class WeatherType {
    CHERRY_BLOSSOM,
    SNOW,
    RAIN,
    AUTUMN_LEAF
}

/**
 * Manages atmospheric season & weather particle effects across the fight arena.
 */
class WeatherSystem {

    class WeatherParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var size: Float,
        var angle: Float,
        var rotateSpeed: Float,
        var color: Int
    )

    var currentType: WeatherType = WeatherType.CHERRY_BLOSSOM
        private set

    private val particles = ArrayList<WeatherParticle>()
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val cherryColors = intArrayOf(
        Color.parseColor("#FFB7C5"),
        Color.parseColor("#FF69B4"),
        Color.parseColor("#FFC0CB"),
        Color.parseColor("#FFE4E1")
    )

    private val autumnColors = intArrayOf(
        Color.parseColor("#FB923C"),
        Color.parseColor("#D97706"),
        Color.parseColor("#EAB308"),
        Color.parseColor("#B45309")
    )

    private val snowColors = intArrayOf(
        Color.parseColor("#FFFFFF"),
        Color.parseColor("#E0F2FE"),
        Color.parseColor("#BAE6FD")
    )

    private val rainColors = intArrayOf(
        Color.parseColor("#93C5FD"),
        Color.parseColor("#60A5FA"),
        Color.parseColor("#3B82F6")
    )

    fun randomizeWeather(screenWidth: Float, screenHeight: Float) {
        synchronized(this) {
            currentType = WeatherType.values().random()
            initParticles(screenWidth, screenHeight)
        }
    }

    private fun initParticles(width: Float, height: Float) {
        particles.clear()
        val count = when (currentType) {
            WeatherType.RAIN -> 90
            WeatherType.SNOW -> 70
            WeatherType.CHERRY_BLOSSOM -> 50
            WeatherType.AUTUMN_LEAF -> 45
        }

        for (i in 0 until count) {
            particles.add(createParticle(width, height, isInitial = true))
        }
    }

    private fun createParticle(width: Float, height: Float, isInitial: Boolean = false): WeatherParticle {
        val startX = (Math.random() * (width + 200f) - 100f).toFloat()
        val startY = if (isInitial) (Math.random() * height).toFloat() else -30f

        return when (currentType) {
            WeatherType.CHERRY_BLOSSOM -> WeatherParticle(
                x = startX,
                y = startY,
                vx = (30f + Math.random() * 60f).toFloat(),
                vy = (50f + Math.random() * 80f).toFloat(),
                size = (8f + Math.random() * 12f).toFloat(),
                angle = (Math.random() * 360f).toFloat(),
                rotateSpeed = (-60f + Math.random() * 120f).toFloat(),
                color = cherryColors.random()
            )

            WeatherType.SNOW -> WeatherParticle(
                x = startX,
                y = startY,
                vx = (-20f + Math.random() * 40f).toFloat(),
                vy = (30f + Math.random() * 50f).toFloat(),
                size = (4f + Math.random() * 8f).toFloat(),
                angle = 0f,
                rotateSpeed = 0f,
                color = snowColors.random()
            )

            WeatherType.RAIN -> WeatherParticle(
                x = startX,
                y = startY,
                vx = (-80f - Math.random() * 40f).toFloat(),
                vy = (600f + Math.random() * 400f).toFloat(),
                size = (18f + Math.random() * 22f).toFloat(),
                angle = 0f,
                rotateSpeed = 0f,
                color = rainColors.random()
            )

            WeatherType.AUTUMN_LEAF -> WeatherParticle(
                x = startX,
                y = startY,
                vx = (-40f + Math.random() * 80f).toFloat(),
                vy = (60f + Math.random() * 90f).toFloat(),
                size = (10f + Math.random() * 14f).toFloat(),
                angle = (Math.random() * 360f).toFloat(),
                rotateSpeed = (-90f + Math.random() * 180f).toFloat(),
                color = autumnColors.random()
            )
        }
    }

    fun update(dt: Float, width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return

        synchronized(this) {
            for (p in particles) {
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.angle += p.rotateSpeed * dt

                // Add gentle horizontal sway for leaves and cherry blossoms
                if (currentType == WeatherType.CHERRY_BLOSSOM || currentType == WeatherType.AUTUMN_LEAF) {
                    p.vx += Math.sin((p.y * 0.02f).toDouble()).toFloat() * 12f * dt
                } else if (currentType == WeatherType.SNOW) {
                    p.vx += Math.sin((p.y * 0.05f).toDouble()).toFloat() * 8f * dt
                }

                // Respawn particle at top when falling off bottom or screen edges
                if (p.y > height + 20f || p.x < -150f || p.x > width + 150f) {
                    val newP = createParticle(width, height, isInitial = false)
                    p.x = newP.x
                    p.y = newP.y
                    p.vx = newP.vx
                    p.vy = newP.vy
                    p.size = newP.size
                    p.angle = newP.angle
                    p.rotateSpeed = newP.rotateSpeed
                    p.color = newP.color
                }
            }
        }
    }

    private val basePetalPath = Path().apply {
        moveTo(0f, -1f)
        cubicTo(0.8f, -0.5f, 0.8f, 0.5f, 0f, 1f)
        cubicTo(-0.8f, 0.5f, -0.8f, -0.5f, 0f, -1f)
        close()
    }

    fun draw(canvas: Canvas) {
        synchronized(this) {
            for (p in particles) {
                when (currentType) {
                    WeatherType.RAIN -> {
                        linePaint.color = p.color
                        linePaint.alpha = 180
                        canvas.drawLine(p.x, p.y, p.x + p.vx * 0.04f, p.y + p.size, linePaint)
                    }

                    WeatherType.SNOW -> {
                        particlePaint.color = p.color
                        particlePaint.alpha = 220
                        canvas.drawCircle(p.x, p.y, p.size, particlePaint)
                    }

                    WeatherType.CHERRY_BLOSSOM, WeatherType.AUTUMN_LEAF -> {
                        canvas.save()
                        canvas.translate(p.x, p.y)
                        canvas.rotate(p.angle)
                        canvas.scale(p.size, p.size)

                        particlePaint.color = p.color
                        particlePaint.alpha = 210

                        canvas.drawPath(basePetalPath, particlePaint)
                        canvas.restore()
                    }
                }
            }
        }
    }
}
