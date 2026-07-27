package com.example.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Custom SurfaceView managing full-screen 2D fighting game rendering, match flow,
 * HUD, and UI overlays.
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var gameThread: GameThread? = null

    // Engine Components
    val spriteLoader = SpriteLoader()
    val effectsManager = EffectsManager()
    val combatSystem = CombatSystem(effectsManager)
    val inputManager = InputManager()
    val aiController = AIController()

    // Match State
    var gameState: GameState = GameState.TITLE_SCREEN
        private set
    var gameMode: GameMode = GameMode.VS_AI

    // Viewport dimensions
    var viewWidth: Float = 1920f
    var viewHeight: Float = 1080f
    var groundY: Float = 810f

    // Fighters
    lateinit var player1: Fighter
    lateinit var player2: Fighter

    // Match Flow Parameters
    var currentRound: Int = 1
    val maxRounds: Int = 3
    var roundTimer: Float = 60f
    var p1Wins: Int = 0
    var p2Wins: Int = 0

    // Intro / Banner timers
    private var bannerTimer: Float = 0f
    private var bannerText: String = "ROUND 1"
    private var subBannerText: String = "READY..."

    // HUD Paints
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        setShadowLayer(12f, 0f, 0f, Color.BLACK)
    }
    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
        textSize = 28f
    }
    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#40000000") }
    private val barBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val p1HealthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2563EB") }
    private val p2HealthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#DC2626") }
    private val catchupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD166") }
    private val gemWinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD700") }
    private val gemEmptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4B5563") }

    // UI Button Hitboxes for menus
    private val btnVsAi = RectF()
    private val btnPractice = RectF()
    private val btnRestart = RectF()
    private val btnMainMenu = RectF()
    private val btnResume = RectF()

    init {
        holder.addCallback(this)
        isFocusable = true
        // Pre-load assets asynchronously
        spriteLoader.loadAssets(scope)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        initGameDimensions(width.toFloat(), height.toFloat())
        startLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        initGameDimensions(width.toFloat(), height.toFloat())
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopLoop()
    }

    private fun initGameDimensions(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        viewWidth = w
        viewHeight = h
        groundY = h * 0.78f

        inputManager.layoutControls(w, h)

        if (!::player1.isInitialized) {
            player1 = Fighter(isPlayer1 = true, x = w * 0.22f, y = groundY - 180f, groundY = groundY)
            player2 = Fighter(isPlayer1 = false, x = w * 0.70f, y = groundY - 180f, groundY = groundY)
        } else {
            player1.groundY = groundY
            player2.groundY = groundY
        }

        // Layout Menu Buttons
        val cx = w / 2f
        val cy = h / 2f
        btnVsAi.set(cx - 220f, cy - 40f, cx + 220f, cy + 30f)
        btnPractice.set(cx - 220f, cy + 50f, cx + 220f, cy + 120f)

        btnRestart.set(cx - 220f, cy + 20f, cx + 220f, cy + 90f)
        btnMainMenu.set(cx - 220f, cy + 110f, cx + 220f, cy + 180f)
        btnResume.set(cx - 220f, cy - 50f, cx + 220f, cy + 10f)
    }

    private fun startLoop() {
        if (gameThread?.isRunning == true) return
        gameThread = GameThread(holder, this).apply {
            isRunning = true
            start()
        }
    }

    private fun stopLoop() {
        gameThread?.let {
            it.isRunning = false
            var retry = true
            while (retry) {
                try {
                    it.join()
                    retry = false
                } catch (e: InterruptedException) {
                    // Retry thread join
                }
            }
        }
        gameThread = null
    }

    fun onDestroy() {
        scope.cancel()
    }

    // --- State Machine Updates ---

    fun update(dt: Float) {
        effectsManager.update(dt)

        when (gameState) {
            GameState.TITLE_SCREEN, GameState.MODE_SELECT -> {
                // Background ambient updates
            }

            GameState.CINEMATIC_INTRO -> {
                bannerTimer -= dt
                if (bannerTimer <= 1.0f && subBannerText == "READY...") {
                    subBannerText = "FIGHT!"
                    effectsManager.triggerScreenShake(0.2f, 15f)
                }
                if (bannerTimer <= 0f) {
                    gameState = GameState.ACTIVE_FIGHTING
                }
            }

            GameState.ACTIVE_FIGHTING -> {
                // Round Timer countdown
                if (gameMode != GameMode.PRACTICE) {
                    roundTimer -= dt
                    if (roundTimer <= 0f) {
                        roundTimer = 0f
                        endRound(timeOut = true)
                        return
                    }
                }

                // AI Logic for Player 2
                if (gameMode == GameMode.VS_AI) {
                    aiController.update(player2, player1, dt)
                }

                // Update Fighters
                player1.update(dt, viewWidth, player2)
                player2.update(dt, viewWidth, player1)

                // Combat System Collisions
                combatSystem.update(player1, player2)

                // Check Knockout Condition
                if (player1.health <= 0f || player2.health <= 0f) {
                    endRound(timeOut = false)
                }
            }

            GameState.ROUND_END -> {
                bannerTimer -= dt
                if (bannerTimer <= 0f) {
                    if (p1Wins >= 2 || p2Wins >= 2) {
                        gameState = GameState.MATCH_END
                    } else {
                        currentRound++
                        startRound()
                    }
                }
            }

            GameState.MATCH_END, GameState.PAUSED -> {
                // Static menu
            }
        }
    }

    private fun startRound() {
        roundTimer = 60f
        player1.resetForRound(viewWidth * 0.22f)
        player2.resetForRound(viewWidth * 0.70f)
        combatSystem.reset()

        bannerTimer = 2.4f
        bannerText = "ROUND $currentRound"
        subBannerText = "READY..."
        gameState = GameState.CINEMATIC_INTRO
    }

    private fun endRound(timeOut: Boolean) {
        gameState = GameState.ROUND_END
        bannerTimer = 2.5f

        if (timeOut) {
            bannerText = "TIME UP!"
            if (player1.health > player2.health) {
                p1Wins++
                subBannerText = "PLAYER 1 WINS ROUND!"
            } else if (player2.health > player1.health) {
                p2Wins++
                subBannerText = "PLAYER 2 WINS ROUND!"
            } else {
                subBannerText = "DRAW ROUND!"
            }
        } else {
            bannerText = "K.O.!"
            effectsManager.triggerScreenShake(0.4f, 25f)
            if (player1.health > 0f) {
                p1Wins++
                subBannerText = "PLAYER 1 WINS ROUND!"
            } else {
                p2Wins++
                subBannerText = "PLAYER 2 WINS ROUND!"
            }
        }
    }

    fun startNewMatch(mode: GameMode) {
        gameMode = mode
        p1Wins = 0
        p2Wins = 0
        currentRound = 1
        effectsManager.clear()
        startRound()
    }

    // --- Rendering Pipeline ---

    fun drawGame(canvas: Canvas) {
        // Apply Camera Screen Shake
        canvas.save()
        canvas.translate(effectsManager.shakeOffsetX, effectsManager.shakeOffsetY)

        // 1. Draw Background Arena
        spriteLoader.drawBackground(canvas, viewWidth, viewHeight, 0f)

        // 2. Draw Shadows on Ground
        drawShadow(canvas, player1)
        drawShadow(canvas, player2)

        // 3. Draw Fighters
        spriteLoader.drawFighter(canvas, player1, getFighterDestRect(player1), player1.facingLeft)
        spriteLoader.drawFighter(canvas, player2, getFighterDestRect(player2), player2.facingLeft)

        // 4. Draw Effects & Particles
        effectsManager.draw(canvas)

        canvas.restore() // End Screen Shake transform

        // 5. Draw HUD & UI Overlays (Fixed to Screen)
        when (gameState) {
            GameState.TITLE_SCREEN -> drawTitleScreen(canvas)
            GameState.MODE_SELECT -> drawModeSelectScreen(canvas)
            GameState.CINEMATIC_INTRO -> {
                drawHUD(canvas)
                drawBanner(canvas, bannerText, subBannerText)
            }
            GameState.ACTIVE_FIGHTING -> {
                drawHUD(canvas)
                inputManager.drawOverlay(canvas)
            }
            GameState.ROUND_END -> {
                drawHUD(canvas)
                drawBanner(canvas, bannerText, subBannerText)
            }
            GameState.MATCH_END -> drawMatchEndScreen(canvas)
            GameState.PAUSED -> drawPauseScreen(canvas)
        }
    }

    private fun getFighterDestRect(f: Fighter): RectF {
        // Render rect slightly larger than collision box for sprite padding
        val extra = 50f
        return RectF(f.x - extra, f.y - extra * 0.5f, f.x + f.width + extra, f.y + f.height + extra * 0.5f)
    }

    private fun drawShadow(canvas: Canvas, f: Fighter) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#60000000") }
        val cx = f.x + f.width / 2f
        val sy = groundY
        val rx = f.width * 0.45f
        val ry = 14f
        canvas.drawOval(cx - rx, sy - ry, cx + rx, sy + ry, shadowPaint)
    }

    // --- HUD & Banners ---

    private fun drawHUD(canvas: Canvas) {
        val pad = 35f
        val barWidth = viewWidth * 0.36f
        val barHeight = 28f
        val topY = 45f

        // --- Player 1 Health Bar (Left) ---
        val p1Rect = RectF(pad, topY, pad + barWidth, topY + barHeight)
        canvas.drawRoundRect(p1Rect, 6f, 6f, barBgPaint)

        // Trailing damage catchup
        val p1CatchupW = (player1.displayHealth / player1.maxHealth) * barWidth
        val p1CatchupRect = RectF(pad, topY, pad + p1CatchupW, topY + barHeight)
        canvas.drawRoundRect(p1CatchupRect, 6f, 6f, catchupPaint)

        // Real health
        val p1RealW = (player1.health / player1.maxHealth) * barWidth
        val p1RealRect = RectF(pad, topY, pad + p1RealW, topY + barHeight)
        canvas.drawRoundRect(p1RealRect, 6f, 6f, p1HealthPaint)
        canvas.drawRoundRect(p1Rect, 6f, 6f, barBorderPaint)

        // P1 Label & Wins
        hudTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("P1: KNIGHT BLUE", pad, topY - 10f, hudTextPaint)
        drawRoundGems(canvas, pad + barWidth - 60f, topY - 18f, p1Wins)

        // --- Player 2 Health Bar (Right) ---
        val p2Right = viewWidth - pad
        val p2Left = p2Right - barWidth
        val p2Rect = RectF(p2Left, topY, p2Right, topY + barHeight)
        canvas.drawRoundRect(p2Rect, 6f, 6f, barBgPaint)

        // Trailing catchup
        val p2CatchupW = (player2.displayHealth / player2.maxHealth) * barWidth
        val p2CatchupRect = RectF(p2Right - p2CatchupW, topY, p2Right, topY + barHeight)
        canvas.drawRoundRect(p2CatchupRect, 6f, 6f, catchupPaint)

        // Real health
        val p2RealW = (player2.health / player2.maxHealth) * barWidth
        val p2RealRect = RectF(p2Right - p2RealW, topY, p2Right, topY + barHeight)
        canvas.drawRoundRect(p2RealRect, 6f, 6f, p2HealthPaint)
        canvas.drawRoundRect(p2Rect, 6f, 6f, barBorderPaint)

        // P2 Label & Wins
        hudTextPaint.textAlign = Paint.Align.RIGHT
        val p2Name = if (gameMode == GameMode.VS_AI) "P2: RED (AI)" else "P2: KNIGHT RED"
        canvas.drawText(p2Name, p2Right, topY - 10f, hudTextPaint)
        drawRoundGems(canvas, p2Left + 10f, topY - 18f, p2Wins)

        // --- Central Timer ---
        val timerCx = viewWidth / 2f
        val timerBg = RectF(timerCx - 50f, topY - 20f, timerCx + 50f, topY + barHeight + 10f)
        canvas.drawRoundRect(timerBg, 12f, 12f, barBgPaint)
        canvas.drawRoundRect(timerBg, 12f, 12f, barBorderPaint)

        hudTextPaint.textAlign = Paint.Align.CENTER
        hudTextPaint.textSize = 36f
        val timerStr = if (gameMode == GameMode.PRACTICE) "∞" else "${roundTimer.toInt()}"
        canvas.drawText(timerStr, timerCx, topY + 24f, hudTextPaint)
        hudTextPaint.textSize = 28f
    }

    private fun drawRoundGems(canvas: Canvas, startX: Float, y: Float, wins: Int) {
        for (i in 0 until 2) {
            val gx = startX + i * 25f
            val paint = if (i < wins) gemWinPaint else gemEmptyPaint
            canvas.drawCircle(gx, y, 8f, paint)
        }
    }

    private fun drawBanner(canvas: Canvas, mainText: String, subText: String) {
        val bannerBg = Paint().apply { color = Color.parseColor("#B0000000") }
        canvas.drawRect(0f, viewHeight * 0.35f, viewWidth, viewHeight * 0.65f, bannerBg)

        titlePaint.color = Color.parseColor("#FFD700")
        titlePaint.textSize = 80f
        canvas.drawText(mainText, viewWidth / 2f, viewHeight * 0.48f, titlePaint)

        titlePaint.color = Color.WHITE
        titlePaint.textSize = 42f
        canvas.drawText(subText, viewWidth / 2f, viewHeight * 0.58f, titlePaint)
    }

    // --- Screen Layouts ---

    private fun drawTitleScreen(canvas: Canvas) {
        val overlayPaint = Paint().apply { color = Color.parseColor("#D00D1117") }
        canvas.drawRect(0f, 0f, viewWidth, viewHeight, overlayPaint)

        titlePaint.color = Color.parseColor("#FFD700")
        titlePaint.textSize = 90f
        canvas.drawText("KNIGHT FIGHT", viewWidth / 2f, viewHeight * 0.38f, titlePaint)

        titlePaint.color = Color.WHITE
        titlePaint.textSize = 36f
        val blink = (System.currentTimeMillis() / 500) % 2 == 0L
        if (blink) {
            canvas.drawText("TAP ANYWHERE TO START", viewWidth / 2f, viewHeight * 0.60f, titlePaint)
        }
    }

    private fun drawModeSelectScreen(canvas: Canvas) {
        val overlayPaint = Paint().apply { color = Color.parseColor("#E00D1117") }
        canvas.drawRect(0f, 0f, viewWidth, viewHeight, overlayPaint)

        titlePaint.color = Color.parseColor("#38BDF8")
        titlePaint.textSize = 65f
        canvas.drawText("SELECT GAME MODE", viewWidth / 2f, viewHeight * 0.28f, titlePaint)

        // VS AI Button
        drawMenuButton(canvas, btnVsAi, "SINGLE PLAYER (VS AI)", Color.parseColor("#2563EB"))
        // Practice Button
        drawMenuButton(canvas, btnPractice, "PRACTICE MODE", Color.parseColor("#059669"))
    }

    private fun drawMatchEndScreen(canvas: Canvas) {
        val overlayPaint = Paint().apply { color = Color.parseColor("#D0000000") }
        canvas.drawRect(0f, 0f, viewWidth, viewHeight, overlayPaint)

        val winnerText = if (p1Wins >= 2) "PLAYER 1 VICTORY!" else "PLAYER 2 VICTORY!"
        titlePaint.color = if (p1Wins >= 2) Color.parseColor("#38BDF8") else Color.parseColor("#EF4444")
        titlePaint.textSize = 75f
        canvas.drawText(winnerText, viewWidth / 2f, viewHeight * 0.35f, titlePaint)

        drawMenuButton(canvas, btnRestart, "PLAY AGAIN", Color.parseColor("#2563EB"))
        drawMenuButton(canvas, btnMainMenu, "MAIN MENU", Color.parseColor("#4B5563"))
    }

    private fun drawPauseScreen(canvas: Canvas) {
        val overlayPaint = Paint().apply { color = Color.parseColor("#B0000000") }
        canvas.drawRect(0f, 0f, viewWidth, viewHeight, overlayPaint)

        titlePaint.color = Color.WHITE
        titlePaint.textSize = 70f
        canvas.drawText("GAME PAUSED", viewWidth / 2f, viewHeight * 0.35f, titlePaint)

        drawMenuButton(canvas, btnResume, "RESUME", Color.parseColor("#059669"))
        drawMenuButton(canvas, btnMainMenu, "MAIN MENU", Color.parseColor("#4B5563"))
    }

    private fun drawMenuButton(canvas: Canvas, rect: RectF, label: String, color: Int) {
        val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        canvas.drawRoundRect(rect, 16f, 16f, btnPaint)
        canvas.drawRoundRect(rect, 16f, 16f, stroke)
        canvas.drawText(label, rect.centerX(), rect.centerY() + 10f, textP)
    }

    // --- Touch Event Dispatching ---

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val index = event.actionIndex
        val pointerId = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)

        when (gameState) {
            GameState.TITLE_SCREEN -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    gameState = GameState.MODE_SELECT
                }
            }

            GameState.MODE_SELECT -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    if (btnVsAi.contains(x, y)) {
                        startNewMatch(GameMode.VS_AI)
                    } else if (btnPractice.contains(x, y)) {
                        startNewMatch(GameMode.PRACTICE)
                    }
                }
            }

            GameState.ACTIVE_FIGHTING -> {
                // Check Pause
                if (inputManager.btnPause.contains(x, y) && action == MotionEvent.ACTION_DOWN) {
                    gameState = GameState.PAUSED
                    return true
                }
                inputManager.handleTouchEvent(action, index, pointerId, x, y, player1)
            }

            GameState.PAUSED -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    if (btnResume.contains(x, y)) {
                        gameState = GameState.ACTIVE_FIGHTING
                    } else if (btnMainMenu.contains(x, y)) {
                        gameState = GameState.TITLE_SCREEN
                    }
                }
            }

            GameState.MATCH_END -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    if (btnRestart.contains(x, y)) {
                        startNewMatch(gameMode)
                    } else if (btnMainMenu.contains(x, y)) {
                        gameState = GameState.TITLE_SCREEN
                    }
                }
            }

            else -> {}
        }
        return true
    }
}
