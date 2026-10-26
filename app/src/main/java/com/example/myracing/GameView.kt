package com.example.myracing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.random.Random

class GameView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs = context.getSharedPreferences("racing_prefs", Context.MODE_PRIVATE)
    private var toneGen: ToneGenerator? = null
    private var isPaused = false

    private var roadLeft = 0f
    private var roadRight = 0f
    private var laneWidth = 0f
    private var playerX = 0f
    private var playerY = 0f
    private var playerW = 0f
    private var playerH = 0f

    private var score = 0
    private var bestScore = 0
    private var gameOver = false
    private var roadOffset = 0f
    private var baseSpeed = 0f
    private var spawnTimer = 0.8f
    private var lastTime = 0L
    private var initialized = false

    private class Enemy(var x: Float, var y: Float, val speed: Float, val w: Float, val h: Float)
    private val enemies = mutableListOf<Enemy>()

    private val roadColor = Color.rgb(45, 45, 50)
    private val grassColor = Color.rgb(35, 110, 55)
    private val playerColor = Color.rgb(220, 45, 45)
    private val enemyColors = intArrayOf(
        Color.rgb(50, 120, 230),
        Color.rgb(245, 200, 40),
        Color.rgb(210, 210, 210),
        Color.rgb(155, 80, 200)
    )

    init {
        bestScore = try {
            prefs.getInt("best", 0).coerceAtLeast(0)
        } catch (e: Exception) { 0 }

        toneGen = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        } catch (e: Exception) { null }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        roadLeft = w * 0.12f
        roadRight = w * 0.88f
        laneWidth = (roadRight - roadLeft) / 3f
        playerW = laneWidth * 0.55f
        playerH = playerW * 1.9f
        playerX = (w - playerW) / 2f
        playerY = h - playerH - 40f
        baseSpeed = h * 0.9f
        initialized = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!initialized || width <= 0 || height <= 0) return

        val now = System.nanoTime()
        val dt = if (lastTime == 0L) 0f else (now - lastTime) / 1_000_000_000f
        lastTime = now
        val delta = dt.coerceAtMost(0.05f)

        if (!gameOver && !isPaused) update(delta)
        render(canvas)
        postInvalidateOnAnimation()
    }

    private fun update(dt: Float) {
        val scroll = baseSpeed * (1f + score / 600f)
        roadOffset = (roadOffset + scroll * dt) % 80f

        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnEnemy()
            spawnTimer = (0.85f - score / 3000f).coerceAtLeast(0.35f) + Random.nextFloat() * 0.25f
        }

        val it = enemies.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.y += (scroll + e.speed) * dt
            if (e.y > height) {
                it.remove()
                score += 10
            }
        }

        val pr = RectF(playerX, playerY, playerX + playerW, playerY + playerH)
        for (e in enemies) {
            val er = RectF(e.x, e.y, e.x + e.w, e.y + e.h)
            if (RectF.intersects(pr, er)) {
                gameOver = true
                try {
                    toneGen?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
                } catch (ex: Exception) { }
                if (score > bestScore) {
                    bestScore = score
                    persistBest()
                }
                break
            }
        }
    }

    private fun persistBest() {
        try {
            prefs.edit().putInt("best", bestScore).apply()
        } catch (e: Exception) { }
    }

    private fun spawnEnemy() {
        val lane = Random.nextInt(3)
        val x = roadLeft + lane * laneWidth + (laneWidth - playerW) / 2f
        for (e in enemies) {
            if (e.y < playerH * 2f && abs(e.x - x) < playerW * 0.9f) return
        }
        val speed = 100f + Random.nextFloat() * 250f
        enemies.add(Enemy(x, -playerH, speed, playerW, playerH))
    }

    private fun render(canvas: Canvas) {
        canvas.drawColor(grassColor)

        paint.style = Paint.Style.FILL
        paint.color = roadColor
        canvas.drawRect(roadLeft, 0f, roadRight, height.toFloat(), paint)

        paint.color = Color.WHITE
        val lineH = 55f
        val gapH = 35f
        for (lane in 1..2) {
            val x = roadLeft + lane * laneWidth
            var y = -roadOffset
            while (y < height) {
                canvas.drawRect(x - 3f, y, x + 3f, y + lineH, paint)
                y += lineH + gapH
            }
        }

        drawCar(canvas, playerX, playerY, playerW, playerH, playerColor, true)
        for (e in enemies) {
            val idx = abs(((e.x / 10f).toInt() + e.speed.toInt())) % enemyColors.size
            drawCar(canvas, e.x, e.y, e.w, e.h, enemyColors[idx], false)
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 55f
        canvas.drawText("Score: $score", 40f, 80f, paint)
        paint.textSize = 36f
        canvas.drawText("Best: $bestScore", 40f, 130f, paint)

        if (gameOver) {
            paint.color = Color.argb(190, 0, 0, 0)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 90f
            canvas.drawText("GAME OVER", width / 2f, height / 2f - 60f, paint)
            paint.textSize = 50f
            canvas.drawText("Score: $score", width / 2f, height / 2f + 20f, paint)
            paint.textSize = 40f
            canvas.drawText("Best: $bestScore", width / 2f, height / 2f + 80f, paint)
            paint.textSize = 45f
            canvas.drawText("Tap to restart", width / 2f, height / 2f + 160f, paint)
        }
    }

    private fun drawCar(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, isPlayer: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = color
        val r = w * 0.18f
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), r, r, paint)

        paint.color = Color.argb(210, 25, 25, 50)
        val wsY = if (isPlayer) y + h * 0.14f else y + h * 0.54f
        canvas.drawRoundRect(RectF(x + w * 0.14f, wsY, x + w * 0.86f, wsY + h * 0.22f), 8f, 8f, paint)

        paint.color = Color.BLACK
        val ww = w * 0.14f
        val wh = h * 0.18f
        canvas.drawRoundRect(RectF(x - ww / 2, y + h * 0.08f, x + ww / 2, y + h * 0.08f + wh), 5f, 5f, paint)
        canvas.drawRoundRect(RectF(x + w - ww / 2, y + h * 0.08f, x + w + ww / 2, y + h * 0.08f + wh), 5f, 5f, paint)
        canvas.drawRoundRect(RectF(x - ww / 2, y + h * 0.74f, x + ww / 2, y + h * 0.74f + wh), 5f, 5f, paint)
        canvas.drawRoundRect(RectF(x + w - ww / 2, y + h * 0.74f, x + w + ww / 2, y + h * 0.74f + wh), 5f, 5f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (gameOver) restart()
                else playerX = (event.x - playerW / 2f).coerceIn(roadLeft, roadRight - playerW)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!gameOver) playerX = (event.x - playerW / 2f).coerceIn(roadLeft, roadRight - playerW)
            }
        }
        return true
    }

    private fun restart() {
        gameOver = false
        score = 0
        enemies.clear()
        playerX = (width - playerW) / 2f
        spawnTimer = 0.8f
        lastTime = 0L
    }

    fun pause() {
        isPaused = true
        lastTime = 0L
    }

    fun destroy() {
        try { toneGen?.release() } catch (e: Exception) { }
        toneGen = null
    }
}
