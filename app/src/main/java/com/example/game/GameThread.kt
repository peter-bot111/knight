package com.example.game

import android.graphics.Canvas
import android.util.Log
import android.view.SurfaceHolder

/**
 * Dedicated high-performance game loop thread. Calculates delta time (dt)
 * targeting 60 FPS to maintain consistent physics across all refresh rates.
 */
class GameThread(
    private val surfaceHolder: SurfaceHolder,
    private val gameView: GameView
) : Thread() {

    @Volatile
    var isRunning: Boolean = false

    private val targetFps = 60
    private val targetFrameTimeNanos = 1_000_000_000L / targetFps

    override fun run() {
        var lastTimeNanos = System.nanoTime()

        while (isRunning) {
            val nowNanos = System.nanoTime()
            val elapsedNanos = nowNanos - lastTimeNanos
            lastTimeNanos = nowNanos

            // Calculate dt in seconds (clamped to prevent physics explosion on lag spikes)
            val dt = (elapsedNanos / 1_000_000_000f).coerceIn(0.001f, 0.05f)

            // Update game engine physics & combat state
            try {
                gameView.update(dt)
            } catch (e: Exception) {
                Log.e("GameThread", "Error during game update: ${e.message}", e)
            }

            // Render to Surface Canvas
            var canvas: Canvas? = null
            try {
                if (isRunning && surfaceHolder.surface.isValid) {
                    canvas = surfaceHolder.lockCanvas()
                    if (canvas != null && isRunning) {
                        synchronized(surfaceHolder) {
                            gameView.drawGame(canvas)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GameThread", "Error during canvas draw: ${e.message}", e)
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        Log.e("GameThread", "Error unlocking canvas: ${e.message}", e)
                    }
                }
            }

            // Sleep remaining frame time if ahead of 60 FPS schedule
            val frameTimeNanos = System.nanoTime() - nowNanos
            val sleepNanos = targetFrameTimeNanos - frameTimeNanos
            if (sleepNanos > 0) {
                try {
                    sleep(sleepNanos / 1_000_000L, (sleepNanos % 1_000_000L).toInt())
                } catch (e: InterruptedException) {
                    // Thread interrupted during shutdown
                }
            }
        }
    }
}
