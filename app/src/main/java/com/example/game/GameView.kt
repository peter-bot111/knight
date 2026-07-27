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
    val weatherSystem = WeatherSystem()
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

    // HUD & UI Pre-allocated Paints (Zero allocation in draw loop)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        setShadowLayer(12f, 0f, 0f, Color.BLACK)
    }
    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
        textSize = 26f
    }
    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#450F172A") }
    private val barBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val p1HealthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2563EB") }
    private val p2HealthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#DC2626") }
    private val catchupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F59E0B") }
    private val gemWinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F59E0B") }
    private val gemEmptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#334155") }

    private val avatarBgPaintP1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E3A8A") }
    private val avatarBgPaintP2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#881337") }
    private val avatarBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#60000000") }
    private val overlayPaint = Paint().apply { color = Color.parseColor("#D00D1117") }
    private val bannerBgPaint = Paint().apply { color = Color.parseColor("#C0000000") }

    private val menuBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val menuBtnStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val menuBtnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    // Pre-allocated Paths and RectFs for zero GC stutter
    private val tempDestRect = RectF()
    private val avatarRectP1 = RectF()
    private val avatarRectP2 = RectF()
    private val timerBgRect = RectF()
    private val bannerRect = RectF()

    private val p1BgPath = Path()
    private val p1CatchupPath = Path()
    private val p1RealPath = Path()
    private val p1BorderPath = Path()

    private val p2BgPath = Path()
    private val p2CatchupPath = Path()
    private val p2RealPath = Path()
    private val p2BorderPath = Path()

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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            initGameDimensions(w.toFloat(), h.toFloat())
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val frame = holder.surfaceFrame
        val w = if (width > 0) width.toFloat() else frame.width().toFloat()
        val h = if (height > 0) height.toFloat() else frame.height().toFloat()
        if (w > 0f && h > 0f) {
            initGameDimensions(w, h)
        }
        startLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width > 0 && height > 0) {
            initGameDimensions(width.toFloat(), height.toFloat())
        }
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
            player1 = Fighter(isPlayer1 = true, x = w * 0.20f, y = groundY - 280f, groundY = groundY)
            player2 = Fighter(isPlayer1 = false, x = w * 0.72f, y = groundY - 280f, groundY = groundY)
        } else {
            player1.groundY = groundY
            player2.groundY = groundY
        }

        // Layout Menu Buttons
        val cx = w / 2f
        val cy = h / 2f
        btnVsAi.set(cx - 240f, cy - 20f, cx + 240f, cy + 60f)

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
        val thread = gameThread ?: return
        thread.isRunning = false
        thread.interrupt()
        var retry = true
        while (retry && thread.isAlive) {
            try {
                thread.join(100)
                if (!thread.isAlive) {
                    retry = false
                }
            } catch (e: InterruptedException) {
                // Keep waiting for thread shutdown
            }
        }
        gameThread = null
    }

    fun onDestroy() {
        scope.cancel()
    }

    // --- State Machine Updates ---

    fun update(dt: Float) {
        if (!::player1.isInitialized || viewWidth <= 0f || viewHeight <= 0f) return

        effectsManager.update(dt)
        weatherSystem.update(dt, viewWidth, viewHeight)

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
                roundTimer -= dt
                if (roundTimer <= 0f) {
                    roundTimer = 0f
                    endRound(timeOut = true)
                    return
                }

                // AI Logic for Player 2
                aiController.update(player2, player1, dt)

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
        weatherSystem.randomizeWeather(viewWidth, viewHeight)
        startRound()
    }

    // --- Rendering Pipeline ---

    fun drawGame(canvas: Canvas) {
        if (!::player1.isInitialized || viewWidth <= 0f || viewHeight <= 0f) {
            canvas.drawColor(Color.parseColor("#0F172A"))
            return
        }

        // Apply Camera Screen Shake
        canvas.save()
        canvas.translate(effectsManager.shakeOffsetX, effectsManager.shakeOffsetY)

        // 1. Draw Background Arena & Ground Tile System (at groundY)
        spriteLoader.drawBackground(canvas, viewWidth, viewHeight, groundY, 0f)

        // 2. Draw Weather Particles over backdrop/ground
        weatherSystem.draw(canvas)

        // 3. Draw Shadows on Ground
        drawShadow(canvas, player1)
        drawShadow(canvas, player2)

        // 4. Draw Fighters
        spriteLoader.drawFighter(canvas, player1, getFighterDestRect(player1), player1.facingLeft)
        spriteLoader.drawFighter(canvas, player2, getFighterDestRect(player2), player2.facingLeft)

        // 5. Draw Combat Effects & Particles
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
        // Render rect aligned flush with groundY at bottom, adjusting for grass height and sprite padding
        val extraX = f.width * 0.25f
        val extraYTop = f.height * 0.22f
        val footOffset = f.height * 0.05f - 14f // Sink into grass slightly, adjust for sprite padding
        tempDestRect.set(f.x - extraX, f.y - extraYTop, f.x + f.width + extraX, f.y + f.height + footOffset)
        return tempDestRect
    }

    private fun drawShadow(canvas: Canvas, f: Fighter) {
        val cx = f.x + f.width / 2f
        val sy = groundY
        val rx = f.width * 0.45f
        val ry = 14f
        canvas.drawOval(cx - rx, sy - ry, cx + rx, sy + ry, shadowPaint)
    }

    // --- AAA Premium HUD & Banners ---

    private fun drawHUD(canvas: Canvas) {
        val topY = 32f
        val barHeight = 32f
        val skew = 12f
        val barWidth = viewWidth * 0.32f
        val avatarSize = 60f

        // --- Player 1 Avatar & Slanted Health Bar ---
        avatarRectP1.set(30f, topY - 5f, 30f + avatarSize, topY - 5f + avatarSize)
        canvas.drawRoundRect(avatarRectP1, 10f, 10f, avatarBgPaintP1)
        canvas.drawRoundRect(avatarRectP1, 10f, 10f, avatarBorderPaint)

        hudTextPaint.textAlign = Paint.Align.CENTER
        hudTextPaint.textSize = 28f
        canvas.drawText("P1", avatarRectP1.centerX(), avatarRectP1.centerY() + 10f, hudTextPaint)

        val p1X = 30f + avatarSize + 12f

        // P1 Background Path
        p1BgPath.reset()
        p1BgPath.moveTo(p1X, topY)
        p1BgPath.lineTo(p1X + barWidth, topY)
        p1BgPath.lineTo(p1X + barWidth - skew, topY + barHeight)
        p1BgPath.lineTo(p1X - skew, topY + barHeight)
        p1BgPath.close()
        canvas.drawPath(p1BgPath, barBgPaint)

        // P1 Trailing Catchup
        val p1CatchupW = (player1.displayHealth / player1.maxHealth) * barWidth
        if (p1CatchupW > 0f) {
            p1CatchupPath.reset()
            p1CatchupPath.moveTo(p1X, topY)
            p1CatchupPath.lineTo(p1X + p1CatchupW, topY)
            p1CatchupPath.lineTo(p1X + p1CatchupW - skew, topY + barHeight)
            p1CatchupPath.lineTo(p1X - skew, topY + barHeight)
            p1CatchupPath.close()
            canvas.drawPath(p1CatchupPath, catchupPaint)
        }

        // P1 Real Health
        val p1RealW = (player1.health / player1.maxHealth) * barWidth
        if (p1RealW > 0f) {
            p1RealPath.reset()
            p1RealPath.moveTo(p1X, topY)
            p1RealPath.lineTo(p1X + p1RealW, topY)
            p1RealPath.lineTo(p1X + p1RealW - skew, topY + barHeight)
            p1RealPath.lineTo(p1X - skew, topY + barHeight)
            p1RealPath.close()
            canvas.drawPath(p1RealPath, p1HealthPaint)
        }

        // P1 Metallic Border
        canvas.drawPath(p1BgPath, barBorderPaint)

        // P1 Name Label & Wins
        hudTextPaint.textAlign = Paint.Align.LEFT
        hudTextPaint.textSize = 24f
        canvas.drawText("BLUE KNIGHT", p1X, topY + barHeight + 24f, hudTextPaint)
        drawRoundGems(canvas, p1X + barWidth - 55f, topY + barHeight + 16f, p1Wins)

        // --- Player 2 Avatar & Slanted Health Bar ---
        avatarRectP2.set(viewWidth - 30f - avatarSize, topY - 5f, viewWidth - 30f, topY - 5f + avatarSize)
        canvas.drawRoundRect(avatarRectP2, 10f, 10f, avatarBgPaintP2)
        canvas.drawRoundRect(avatarRectP2, 10f, 10f, avatarBorderPaint)

        hudTextPaint.textAlign = Paint.Align.CENTER
        hudTextPaint.textSize = 28f
        canvas.drawText("P2", avatarRectP2.centerX(), avatarRectP2.centerY() + 10f, hudTextPaint)

        val p2Right = viewWidth - 30f - avatarSize - 12f
        val p2Left = p2Right - barWidth

        // P2 Background Path
        p2BgPath.reset()
        p2BgPath.moveTo(p2Left, topY)
        p2BgPath.lineTo(p2Right, topY)
        p2BgPath.lineTo(p2Right + skew, topY + barHeight)
        p2BgPath.lineTo(p2Left + skew, topY + barHeight)
        p2BgPath.close()
        canvas.drawPath(p2BgPath, barBgPaint)

        // P2 Trailing Catchup
        val p2CatchupW = (player2.displayHealth / player2.maxHealth) * barWidth
        if (p2CatchupW > 0f) {
            p2CatchupPath.reset()
            p2CatchupPath.moveTo(p2Right - p2CatchupW, topY)
            p2CatchupPath.lineTo(p2Right, topY)
            p2CatchupPath.lineTo(p2Right + skew, topY + barHeight)
            p2CatchupPath.lineTo(p2Right - p2CatchupW + skew, topY + barHeight)
            p2CatchupPath.close()
            canvas.drawPath(p2CatchupPath, catchupPaint)
        }

        // P2 Real Health
        val p2RealW = (player2.health / player2.maxHealth) * barWidth
        if (p2RealW > 0f) {
            p2RealPath.reset()
            p2RealPath.moveTo(p2Right - p2RealW, topY)
            p2RealPath.lineTo(p2Right, topY)
            p2RealPath.lineTo(p2Right + skew, topY + barHeight)
            p2RealPath.lineTo(p2Right - p2RealW + skew, topY + barHeight)
            p2RealPath.close()
            canvas.drawPath(p2RealPath, p2HealthPaint)
        }

        // P2 Metallic Border
        canvas.drawPath(p2BgPath, barBorderPaint)

        // P2 Name Label & Wins
        hudTextPaint.textAlign = Paint.Align.RIGHT
        hudTextPaint.textSize = 24f
        canvas.drawText("RED KNIGHT (AI)", p2Right, topY + barHeight + 24f, hudTextPaint)
        drawRoundGems(canvas, p2Left + 10f, topY + barHeight + 16f, p2Wins)

        // --- Central Round Timer ---
        val timerCx = viewWidth / 2f
        timerBgRect.set(timerCx - 45f, topY - 5f, timerCx + 45f, topY + barHeight + 8f)
        canvas.drawRoundRect(timerBgRect, 10f, 10f, barBgPaint)
        canvas.drawRoundRect(timerBgRect, 10f, 10f, barBorderPaint)

        hudTextPaint.textAlign = Paint.Align.CENTER
        hudTextPaint.textSize = 34f
        canvas.drawText("${roundTimer.toInt()}", timerCx, topY + 28f, hudTextPaint)
    }

    private fun drawRoundGems(canvas: Canvas, startX: Float, y: Float, wins: Int) {
        for (i in 0 until 2) {
            val gx = startX + i * 25f
            val paint = if (i < wins) gemWinPaint else gemEmptyPaint
            canvas.drawCircle(gx, y, 8f, paint)
        }
    }

    private fun drawBanner(canvas: Canvas, mainText: String, subText: String) {
        bannerRect.set(0f, viewHeight * 0.35f, viewWidth, viewHeight * 0.65f)
        canvas.drawRect(bannerRect, bannerBgPaint)

        titlePaint.color = Color.parseColor("#FFD700")
        titlePaint.textSize = 80f
        canvas.drawText(mainText, viewWidth / 2f, viewHeight * 0.48f, titlePaint)

        titlePaint.color = Color.WHITE
        titlePaint.textSize = 42f
        canvas.drawText(subText, viewWidth / 2f, viewHeight * 0.58f, titlePaint)
    }

    // --- Screen Layouts ---

    private fun drawTitleScreen(canvas: Canvas) {
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
        canvas.drawRect(0f, 0f, viewWidth, viewHeight, overlayPaint)

        titlePaint.color = Color.parseColor("#38BDF8")
        titlePaint.textSize = 65f
        canvas.drawText("SELECT MATCH", viewWidth / 2f, viewHeight * 0.32f, titlePaint)

        // Prominent Single Player VS AI Button
        drawMenuButton(canvas, btnVsAi, "START MATCH (VS AI)", Color.parseColor("#2563EB"))
    }

    private fun drawMatchEndScreen(canvas: Canvas) {
        canvas.drawRect(0f, 0f, viewWidth, viewHeight, overlayPaint)

        val winnerText = if (p1Wins >= 2) "PLAYER 1 VICTORY!" else "PLAYER 2 VICTORY!"
        titlePaint.color = if (p1Wins >= 2) Color.parseColor("#38BDF8") else Color.parseColor("#EF4444")
        titlePaint.textSize = 75f
        canvas.drawText(winnerText, viewWidth / 2f, viewHeight * 0.35f, titlePaint)

        drawMenuButton(canvas, btnRestart, "PLAY AGAIN", Color.parseColor("#2563EB"))
        drawMenuButton(canvas, btnMainMenu, "MAIN MENU", Color.parseColor("#4B5563"))
    }

    private fun drawPauseScreen(canvas: Canvas) {
        canvas.drawRect(0f, 0f, viewWidth, viewHeight, overlayPaint)

        titlePaint.color = Color.WHITE
        titlePaint.textSize = 70f
        canvas.drawText("GAME PAUSED", viewWidth / 2f, viewHeight * 0.35f, titlePaint)

        drawMenuButton(canvas, btnResume, "RESUME", Color.parseColor("#059669"))
        drawMenuButton(canvas, btnMainMenu, "MAIN MENU", Color.parseColor("#4B5563"))
    }

    private fun drawMenuButton(canvas: Canvas, rect: RectF, label: String, color: Int) {
        menuBtnPaint.color = color
        canvas.drawRoundRect(rect, 16f, 16f, menuBtnPaint)
        canvas.drawRoundRect(rect, 16f, 16f, menuBtnStrokePaint)
        canvas.drawText(label, rect.centerX(), rect.centerY() + 10f, menuBtnTextPaint)
    }

    // --- Touch Event Dispatching ---

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!::player1.isInitialized || viewWidth <= 0f || viewHeight <= 0f) return true

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
                    }
                }
            }

            GameState.ACTIVE_FIGHTING -> {
                // Check Pause Button
                if (inputManager.btnPause.contains(x, y) && action == MotionEvent.ACTION_DOWN) {
                    gameState = GameState.PAUSED
                    return true
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    for (i in 0 until event.pointerCount) {
                        val pid = event.getPointerId(i)
                        val px = event.getX(i)
                        val py = event.getY(i)
                        inputManager.handleTouchEvent(action, i, pid, px, py, player1)
                    }
                } else {
                    inputManager.handleTouchEvent(action, index, pointerId, x, y, player1)
                }
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
