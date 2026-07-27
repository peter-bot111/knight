package com.example.game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Handles asynchronous fetching, caching, and slicing of 2D character sprite sheets
 * and background images. Also provides procedural canvas rendering fallbacks.
 */
class SpriteLoader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Cache: Key = (IsPlayer1, FighterState) -> List of sliced frame Bitmaps
    private val spriteCache = ConcurrentHashMap<Pair<Boolean, FighterState>, List<Bitmap>>()

    // Loaded match background image
    @Volatile
    var currentBackgroundBitmap: Bitmap? = null
        private set

    @Volatile
    var currentBgIndex: Int = 1
        private set

    @Volatile
    var isLoaded: Boolean = false
        private set

    private val P1_BASE_URL = "https://raw.githubusercontent.com/naveenkumarr24cs-a11y/2D-FIGHT-GAME1/main/Character%20Colour1/Outline/120x80_PNGSheets/"
    private val P2_BASE_URL = "https://raw.githubusercontent.com/naveenkumarr24cs-a11y/2D-FIGHT-GAME1/main/Character%20Colour2/Outline/120x80_PNGSheets/"

    private val FRAME_WIDTH = 120
    private val FRAME_HEIGHT = 80

    // Paints for dynamic fallback vector knights
    private val p1ArmorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2563EB") }
    private val p1CapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3B82F6") }
    private val p2ArmorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#DC2626") }
    private val p2CapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF4444") }
    private val steelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E2E8F0") }
    private val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F59E0B") }
    private val darkMetalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E293B") }
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }

    fun loadAssets(scope: CoroutineScope, onProgress: (Float) -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            try {
                // Select random background (1 to 15)
                currentBgIndex = (1..15).random()
                loadBackgroundImage(currentBgIndex)

                val statesToLoad = listOf(
                    FighterState.IDLE to "_Idle.png",
                    FighterState.RUN to "_Run.png",
                    FighterState.JUMP to "_Jump.png",
                    FighterState.FALL to "_Fall.png",
                    FighterState.CROUCH to "_Crouch.png",
                    FighterState.LIGHT_ATTACK to "_Attack.png",
                    FighterState.HEAVY_ATTACK to "_Attack2.png",
                    FighterState.COMBO_ATTACK to "_AttackCombo.png",
                    FighterState.ROLL to "_Roll.png",
                    FighterState.HIT_STUN to "_Hit.png",
                    FighterState.DEATH to "_Death.png"
                )

                var totalLoaded = 0
                val totalItems = statesToLoad.size * 2

                for ((state, fileName) in statesToLoad) {
                    // Load P1
                    loadAndSliceSpriteSheet(isP1 = true, state = state, url = P1_BASE_URL + fileName)
                    totalLoaded++
                    onProgress(totalLoaded.toFloat() / totalItems)

                    // Load P2
                    loadAndSliceSpriteSheet(isP1 = false, state = state, url = P2_BASE_URL + fileName)
                    totalLoaded++
                    onProgress(totalLoaded.toFloat() / totalItems)
                }

                isLoaded = true
                Log.d("SpriteLoader", "Successfully loaded sprite assets.")
            } catch (e: Exception) {
                Log.e("SpriteLoader", "Failed to load remote sprite assets, using dynamic vector fallbacks: ${e.message}")
            }
        }
    }

    private suspend fun loadBackgroundImage(bgIndex: Int) {
        val bgUrl = "https://raw.githubusercontent.com/naveenkumarr24cs-a11y/2D-FIGHT-GAME1/main/background/bg_$bgIndex/use$bgIndex.png"
        try {
            val request = Request.Builder().url(bgUrl).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { inputStream ->
                    currentBackgroundBitmap = BitmapFactory.decodeStream(inputStream)
                }
            }
        } catch (e: Exception) {
            Log.w("SpriteLoader", "Could not load background image from $bgUrl: ${e.message}")
        }
    }

    private fun loadAndSliceSpriteSheet(isP1: Boolean, state: FighterState, url: String) {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { inputStream ->
                    val fullBitmap = BitmapFactory.decodeStream(inputStream)
                    if (fullBitmap != null) {
                        val frameCount = fullBitmap.width / FRAME_WIDTH
                        if (frameCount > 0) {
                            val frames = ArrayList<Bitmap>(frameCount)
                            for (i in 0 until frameCount) {
                                val x = i * FRAME_WIDTH
                                if (x + FRAME_WIDTH <= fullBitmap.width && FRAME_HEIGHT <= fullBitmap.height) {
                                    val sliced = Bitmap.createBitmap(fullBitmap, x, 0, FRAME_WIDTH, FRAME_HEIGHT)
                                    frames.add(sliced)
                                }
                            }
                            if (frames.isNotEmpty()) {
                                spriteCache[Pair(isP1, state)] = frames
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SpriteLoader", "Error fetching sprite sheet $url: ${e.message}")
        }
    }

    fun getFrame(isP1: Boolean, state: FighterState, frameIndex: Int): Bitmap? {
        val frames = spriteCache[Pair(isP1, state)]
            ?: spriteCache[Pair(isP1, FighterState.IDLE)]
        if (frames.isNullOrEmpty()) return null
        return frames[frameIndex % frames.size]
    }

    /**
     * Draws the fighter using loaded bitmap frames if available, or procedural vector knights.
     */
    fun drawFighter(
        canvas: Canvas,
        fighter: Fighter,
        destRect: RectF,
        facingLeft: Boolean
    ) {
        val frameBitmap = getFrame(fighter.isPlayer1, fighter.state, fighter.currentFrameIndex)

        if (frameBitmap != null) {
            canvas.save()
            if (facingLeft) {
                canvas.scale(-1f, 1f, destRect.centerX(), destRect.centerY())
            }
            val srcRect = Rect(0, 0, frameBitmap.width, frameBitmap.height)
            canvas.drawBitmap(frameBitmap, srcRect, destRect, null)
            canvas.restore()
        } else {
            // Draw stylized procedural knight
            drawProceduralKnight(canvas, fighter, destRect, facingLeft)
        }
    }

    /**
     * Renders a highly polished procedural vector Knight if sprite sheets are loading/unavailable.
     */
    private fun drawProceduralKnight(
        canvas: Canvas,
        fighter: Fighter,
        rect: RectF,
        facingLeft: Boolean
    ) {
        canvas.save()
        val cx = rect.centerX()
        val cy = rect.bottom - rect.height() * 0.45f
        val scale = rect.height() / 120f

        val armorPaint = if (fighter.isPlayer1) p1ArmorPaint else p2ArmorPaint
        val capePaint = if (fighter.isPlayer1) p1CapePaint else p2CapePaint

        // Facing direction scale
        val dir = if (facingLeft) -1f else 1f
        canvas.scale(dir, 1f, cx, cy)

        // Hit flash or invulnerability tint
        if (fighter.state == FighterState.HIT_STUN) {
            auraPaint.color = Color.RED
            canvas.drawCircle(cx, cy, 50f * scale, auraPaint)
        } else if (fighter.state == FighterState.ROLL) {
            auraPaint.color = Color.CYAN
            canvas.drawCircle(cx, cy, 50f * scale, auraPaint)
        }

        // Animated leg positions based on state
        val animTime = System.currentTimeMillis() / 100.0
        val legOffset = if (fighter.state == FighterState.RUN) Math.sin(animTime).toFloat() * 20f * scale else 0f

        // Cape
        val capePath = Path().apply {
            moveTo(cx - 15f * scale, cy - 30f * scale)
            lineTo(cx - 35f * scale - legOffset * 0.5f, cy + 30f * scale)
            lineTo(cx - 5f * scale, cy + 35f * scale)
            close()
        }
        canvas.drawPath(capePath, capePaint)

        // Legs & Boots
        canvas.drawRoundRect(cx - 18f * scale + legOffset, cy + 10f * scale, cx - 6f * scale + legOffset, cy + 45f * scale, 6f, 6f, darkMetalPaint)
        canvas.drawRoundRect(cx + 6f * scale - legOffset, cy + 10f * scale, cx + 18f * scale - legOffset, cy + 45f * scale, 6f, 6f, darkMetalPaint)

        // Torso / Plate Armor
        val torsoRect = RectF(cx - 20f * scale, cy - 25f * scale, cx + 20f * scale, cy + 15f * scale)
        canvas.drawRoundRect(torsoRect, 8f, 8f, armorPaint)

        // Golden Emblem on chest
        canvas.drawCircle(cx, cy - 5f * scale, 6f * scale, goldPaint)

        // Helmet
        val headCy = cy - 40f * scale
        canvas.drawCircle(cx, headCy, 18f * scale, darkMetalPaint)
        // Visor slit
        canvas.drawRect(cx - 4f * scale, headCy - 4f * scale, cx + 16f * scale, headCy + 4f * scale, goldPaint)

        // Sword & Arms
        val swordAngle = when (fighter.state) {
            FighterState.LIGHT_ATTACK -> 45f
            FighterState.HEAVY_ATTACK -> 75f
            FighterState.COMBO_ATTACK -> -30f + (Math.sin(animTime * 3) * 60f).toFloat()
            FighterState.BLOCKING -> -60f
            else -> 15f
        }

        canvas.save()
        canvas.translate(cx + 15f * scale, cy - 10f * scale)
        canvas.rotate(swordAngle)

        // Sword Hilt
        canvas.drawRect(-5f * scale, -4f * scale, 5f * scale, 4f * scale, goldPaint)
        // Sword Blade
        val bladePath = Path().apply {
            moveTo(-3f * scale, -4f * scale)
            lineTo(45f * scale, -2f * scale)
            lineTo(55f * scale, 0f)
            lineTo(45f * scale, 2f * scale)
            lineTo(-3f * scale, 4f * scale)
            close()
        }
        canvas.drawPath(bladePath, steelPaint)

        // Glow on heavy attack
        if (fighter.state == FighterState.HEAVY_ATTACK || fighter.state == FighterState.COMBO_ATTACK) {
            auraPaint.color = if (fighter.isPlayer1) Color.YELLOW else Color.MAGENTA
            canvas.drawPath(bladePath, auraPaint)
        }

        canvas.restore()
        canvas.restore()
    }

    /**
     * Draws background bitmap if loaded or procedural backdrop, followed by a grounded 2D floor tile platform.
     */
    fun drawBackground(canvas: Canvas, width: Float, height: Float, groundY: Float, cameraOffset: Float = 0f) {
        val bg = currentBackgroundBitmap
        if (bg != null) {
            val src = Rect(0, 0, bg.width, bg.height)
            val dest = Rect(0, 0, width.toInt(), height.toInt())
            canvas.drawBitmap(bg, src, dest, null)
        } else {
            // Draw procedural 2D arena with parallax mountain/sky and glowing moon
            val skyPaint = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, height * 0.75f, Color.parseColor("#0F172A"), Color.parseColor("#1E1B4B"), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, width, height, skyPaint)

            // Moon
            val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FDE68A") }
            canvas.drawCircle(width * 0.8f, height * 0.25f, 50f, moonPaint)

            // Parallax Mountains
            val mountainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#312E81") }
            val mPath = Path().apply {
                moveTo(0f, groundY)
                lineTo(width * 0.2f, height * 0.45f)
                lineTo(width * 0.45f, height * 0.65f)
                lineTo(width * 0.75f, height * 0.4f)
                lineTo(width, groundY)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            canvas.drawPath(mPath, mountainPaint)
        }

        // --- Ground Tile System (Grounds Fighters at groundY) ---
        val dirtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4D3319") // Solid dirt brown floor
        }
        val grassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2D862D") // Vibrant grass top
        }
        val stoneAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A531A") // Darker grass trim line
        }

        // 1. Dirt layer from groundY to bottom of screen
        canvas.drawRect(0f, groundY, width, height, dirtPaint)

        // 2. Thin grass top tile layer right at groundY line (14dp high)
        val grassHeight = 14f
        canvas.drawRect(0f, groundY - grassHeight, width, groundY, grassPaint)
        canvas.drawRect(0f, groundY, width, groundY + 4f, stoneAccentPaint)
    }
}
