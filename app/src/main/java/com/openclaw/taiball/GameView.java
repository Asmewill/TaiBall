package com.openclaw.taiball;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class GameView extends View {
    
    // 游戏常量
    private static final float FRICTION = 0.985f;
    private static final int BALL_RADIUS_DP = 12;
    private static final int POCKET_RADIUS_DP = 25;
    
    // 球的颜色
    private static final int[] BALL_COLORS = {
        Color.WHITE,      // 白球
        0xFFFFD700,       // 1 - 黄色
        0xFF0000FF,       // 2 - 蓝色
        0xFFFF0000,       // 3 - 红色
        0xFF800080,       // 4 - 紫色
        0xFFFFA500,       // 5 - 橙色
        0xFF008000,       // 6 - 绿色
        0xFF8B4513,       // 7 - 棕色
        Color.BLACK,      // 8 - 黑球
        0xFFFFD700,       // 9 - 黄色
        0xFF0000FF,       // 10 - 蓝色
        0xFFFF0000,       // 11 - 红色
        0xFF800080,       // 12 - 紫色
        0xFFFFA500,       // 13 - 橙色
        0xFF008000,       // 14 - 绿色
        0xFF8B4513        // 15 - 棕色
    };
    
    private List<Ball> balls;
    private Ball cueBall;
    private Paint tablePaint;
    private Paint ballPaint;
    private Paint textPaint;
    private Paint cuePaint;
    private Paint pocketPaint;
    private Paint linePaint;
    
    private float tableX;
    private float tableY;
    private float tableWidth;
    private float tableHeight;
    private float ballRadius;
    private float pocketRadius;
    
    private boolean isDragging = false;
    private float dragStartX, dragStartY;
    private float dragEndX, dragEndY;
    
    private int score = 0;
    private boolean gameOver = false;
    private boolean isPaused = false;
    
    private ScoreListener scoreListener;
    
    public interface ScoreListener {
        void onScoreUpdate(int score);
        void onGameWin();
        void onGameLose();
    }
    
    public GameView(Context context) {
        super(context);
        init();
    }
    
    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public GameView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        balls = new ArrayList<>();
        
        tablePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tablePaint.setColor(Color.parseColor("#0d6e3a"));
        
        ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ballPaint.setStyle(Paint.Style.FILL);
        
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        
        cuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cuePaint.setStyle(Paint.Style.FILL);
        
        pocketPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pocketPaint.setColor(Color.parseColor("#1a1a1a"));
        
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#66FFFFFF"));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2);
        linePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10, 5}, 0));
        
        // 启动游戏循环
        startGameLoop();
    }
    
    private void startGameLoop() {
        post(new Runnable() {
            @Override
            public void run() {
                if (!isPaused) {
                    update();
                }
                invalidate();
                postDelayed(this, 16); // ~60 FPS
            }
        });
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        // 计算 3:1 宽高比的桌面大小，并在屏幕中间显示
        float padding = 40f; // 四周留白
        float maxTableWidth = w - padding * 2;
        float maxTableHeight = h - padding * 2;
        
        // 保持 3:1 比例
        float widthRatio = maxTableWidth / 3f;
        float heightRatio = maxTableHeight / 1f;
        
        // 以较小的一侧为准
        if (widthRatio < heightRatio) {
            tableWidth = maxTableWidth;
            tableHeight = tableWidth / 3f; // 3:1 比例
        } else {
            tableHeight = maxTableHeight;
            tableWidth = tableHeight * 3f; // 3:1 比例
        }
        
        // 桌面的起始位置（居中显示）
        tableX = (w - tableWidth) / 2f;
        tableY = (h - tableHeight) / 2f;
        
        // 增大台球大小，能看清数字
        ballRadius = tableHeight / 20f; // 原来是 /40f，现在增大一倍
        pocketRadius = ballRadius * 1.5f; // 袋口适当缩小，显得球更大
        
        if (balls.isEmpty()) {
            initGame();
        }
    }
    
    private void initGame() {
        balls.clear();
        score = 0;
        gameOver = false;
        
        if (scoreListener != null) {
            scoreListener.onScoreUpdate(0);
        }
        
        // 白球位置（考虑桌面偏移）
        float cueX = tableX + tableWidth * 0.25f;
        float cueY = tableY + tableHeight / 2;
        cueBall = new Ball(cueX, cueY, BALL_COLORS[0], 0);
        balls.add(cueBall);
        
        // 摆球（三角形），考虑桌面偏移
        float startX = tableX + tableWidth * 0.7f;
        float startY = tableY + tableHeight / 2;
        float ballSpacing = ballRadius * 2.2f;
        int ballIndex = 1;
        
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col <= row; col++) {
                float x = startX + row * ballSpacing * 0.866f;
                float y = startY - (row * ballSpacing) / 2 + col * ballSpacing;
                balls.add(new Ball(x, y, BALL_COLORS[ballIndex], ballIndex));
                ballIndex++;
            }
        }
    }
    
    private void update() {
        if (gameOver) return;
        
        // 更新所有球
        for (Ball ball : balls) {
            if (!ball.potted) {
                ball.update();
            }
        }
        
        // 检查碰撞
        checkCollisions();
        
        // 检查袋口
        checkPockets();
        
        // 检查游戏结束
        checkGameOver();
    }
    
    private void checkCollisions() {
        for (int i = 0; i < balls.size(); i++) {
            for (int j = i + 1; j < balls.size(); j++) {
                Ball b1 = balls.get(i);
                Ball b2 = balls.get(j);
                
                if (b1.potted || b2.potted) continue;
                
                float dx = b2.x - b1.x;
                float dy = b2.y - b1.y;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                
                if (distance < ballRadius * 2) {
                    // 碰撞响应
                    float angle = (float) Math.atan2(dy, dx);
                    float sin = (float) Math.sin(angle);
                    float cos = (float) Math.cos(angle);
                    
                    float vx1 = b1.vx * cos + b1.vy * sin;
                    float vy1 = b1.vy * cos - b1.vx * sin;
                    float vx2 = b2.vx * cos + b2.vy * sin;
                    float vy2 = b2.vy * cos - b2.vx * sin;
                    
                    b1.vx = vx2 * cos - vy1 * sin;
                    b1.vy = vy1 * cos + vx2 * sin;
                    b2.vx = vx1 * cos - vy2 * sin;
                    b2.vy = vy2 * cos + vx1 * sin;
                    
                    // 分离球
                    float overlap = (ballRadius * 2 - distance) / 2;
                    b1.x -= overlap * Math.cos(angle);
                    b1.y -= overlap * Math.sin(angle);
                    b2.x += overlap * Math.cos(angle);
                    b2.y += overlap * Math.sin(angle);
                }
            }
        }
    }
    
    private void checkPockets() {
        float[][] pockets = {
            {tableX + 0, tableY + 0},
            {tableX + tableWidth / 2, tableY + 0},
            {tableX + tableWidth, tableY + 0},
            {tableX + 0, tableY + tableHeight},
            {tableX + tableWidth / 2, tableY + tableHeight},
            {tableX + tableWidth, tableY + tableHeight}
        };
        
        for (Ball ball : balls) {
            if (ball.potted) continue;
            
            for (float[] pocket : pockets) {
                float dx = ball.x - pocket[0];
                float dy = ball.y - pocket[1];
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                
                if (distance < pocketRadius) {
                    potBall(ball);
                    break;
                }
            }
        }
    }
    
    private void potBall(Ball ball) {
        if (ball.number == 0) {
            // 白球入袋
            ball.potted = true;
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    ball.potted = false;
                    ball.x = tableX + tableWidth * 0.25f;
                    ball.y = tableY + tableHeight / 2;
                    ball.vx = 0;
                    ball.vy = 0;
                }
            }, 1000);
        } else if (ball.number == 8) {
            // 8 号球
            int remainingBalls = 0;
            for (Ball b : balls) {
                if (b.number != 0 && b.number != 8 && !b.potted) {
                    remainingBalls++;
                }
            }
            
            if (remainingBalls == 0) {
                gameOver = true;
                if (scoreListener != null) {
                    scoreListener.onGameWin();
                }
            } else {
                gameOver = true;
                if (scoreListener != null) {
                    scoreListener.onGameLose();
                }
            }
        } else {
            ball.potted = true;
            score += 10;
            if (scoreListener != null) {
                scoreListener.onScoreUpdate(score);
            }
        }
    }
    
    private void checkGameOver() {
        // 检查是否所有球都停止
        boolean allStopped = true;
        for (Ball ball : balls) {
            if (!ball.potted && (Math.abs(ball.vx) > 0.1f || Math.abs(ball.vy) > 0.1f)) {
                allStopped = false;
                break;
            }
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 绘制球桌背景（考虑偏移）
        canvas.drawRect(tableX, tableY, tableX + tableWidth, tableY + tableHeight, tablePaint);
        
        // 绘制袋口（考虑偏移）
        float[][] pockets = {
            {tableX + 0, tableY + 0},
            {tableX + tableWidth / 2, tableY + 0},
            {tableX + tableWidth, tableY + 0},
            {tableX + 0, tableY + tableHeight},
            {tableX + tableWidth / 2, tableY + tableHeight},
            {tableX + tableWidth, tableY + tableHeight}
        };
        
        for (float[] pocket : pockets) {
            canvas.drawCircle(pocket[0], pocket[1], pocketRadius, pocketPaint);
        }
        
        // 绘制开球线（考虑偏移）
        canvas.drawLine(
            tableX + tableWidth * 0.25f, tableY + 0,
            tableX + tableWidth * 0.25f, tableY + tableHeight,
            linePaint
        );
        
        // 绘制球
        for (Ball ball : balls) {
            if (!ball.potted) {
                drawBall(canvas, ball);
            }
        }
        
        // 绘制球杆
        if (isDragging && !cueBall.potted && !gameOver) {
            drawCue(canvas);
        }
        
        // 绘制力度
        if (isDragging) {
            float dx = dragStartX - dragEndX;
            float dy = dragStartY - dragEndY;
            float power = Math.min((float) Math.sqrt(dx * dx + dy * dy) / 5, 100);
            
            textPaint.setTextSize(30);
            textPaint.setColor(Color.WHITE);
            canvas.drawText("力度：" + (int)power, tableX + 20, tableY - 10, textPaint);
        }
    }
    
    private void drawBall(Canvas canvas, Ball ball) {
        // 球体渐变
        RadialGradient gradient = new RadialGradient(
            ball.x - ballRadius / 3,
            ball.y - ballRadius / 3,
            ballRadius,
            ball.color,
            darkenColor(ball.color),
            Shader.TileMode.CLAMP
        );
        
        Paint ballPaintLocal = new Paint(Paint.ANTI_ALIAS_FLAG);
        ballPaintLocal.setShader(gradient);
        
        canvas.drawCircle(ball.x, ball.y, ballRadius, ballPaintLocal);
        
        // 绘制数字
        if (ball.number > 0) {
            canvas.drawCircle(ball.x, ball.y, ballRadius * 0.6f, ballPaint);
            ballPaint.setColor(Color.WHITE);
            textPaint.setTextSize(ballRadius * 0.8f);
            textPaint.setColor(Color.BLACK);
            canvas.drawText(String.valueOf(ball.number), ball.x, ball.y + ballRadius * 0.1f, textPaint);
            ballPaint.setColor(ball.color);
        }
        
        // 高光
        Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setColor(Color.argb(100, 255, 255, 255));
        canvas.drawCircle(ball.x - ballRadius / 3, ball.y - ballRadius / 3, ballRadius / 3, highlightPaint);
    }
    
    private void drawCue(Canvas canvas) {
        float dx = dragStartX - dragEndX;
        float dy = dragStartY - dragEndY;
        float angle = (float) Math.atan2(dy, dx);
        float power = Math.min((float) Math.sqrt(dx * dx + dy * dy) / 5, 100);
        
        canvas.save();
        canvas.translate(cueBall.x, cueBall.y);
        canvas.rotate((float) Math.toDegrees(angle));
        
        // 瞄准线
        linePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10, 5}, 0));
        canvas.drawLine(0, 0, 200, 0, linePaint);
        linePaint.setPathEffect(null);
        
        // 球杆
        LinearGradient cueGradient = new LinearGradient(
            -150 - power, 0, -20, 0,
            0xFF8B4513, 0xFFDEB887,
            Shader.TileMode.CLAMP
        );
        cuePaint.setShader(cueGradient);
        canvas.drawRect(-150 - power, -ballRadius / 2, -20, ballRadius / 2, cuePaint);
        
        canvas.restore();
    }
    
    private int darkenColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.7f;
        return Color.HSVToColor(hsv);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gameOver || cueBall.potted) return true;
        
        float x = event.getX();
        float y = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                float dx = x - cueBall.x;
                float dy = y - cueBall.y;
                if (Math.sqrt(dx * dx + dy * dy) < ballRadius * 3) {
                    isDragging = true;
                    dragStartX = cueBall.x;
                    dragStartY = cueBall.y;
                    dragEndX = x;
                    dragEndY = y;
                }
                break;
                
            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    dragEndX = x;
                    dragEndY = y;
                }
                break;
                
            case MotionEvent.ACTION_UP:
                if (isDragging) {
                    float dragDx = dragStartX - dragEndX;
                    float dragDy = dragStartY - dragEndY;
                    float power = Math.min((float) Math.sqrt(dragDx * dragDx + dragDy * dragDy) / 20, 15);
                    float angle = (float) Math.atan2(dragDy, dragDx);
                    
                    cueBall.vx = (float) (Math.cos(angle) * power);
                    cueBall.vy = (float) (Math.sin(angle) * power);
                    
                    isDragging = false;
                }
                break;
        }
        
        return true;
    }
    
    public void resetGame() {
        initGame();
    }
    
    public int getScore() {
        return score;
    }
    
    public void setScoreListener(ScoreListener listener) {
        this.scoreListener = listener;
    }
    
    public void pauseGame() {
        isPaused = true;
    }
    
    public void resumeGame() {
        isPaused = false;
    }
    
    // 球类
    private class Ball {
        float x, y;
        float vx, vy;
        int color;
        int number;
        boolean potted;
        
        Ball(float x, float y, int color, int number) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.number = number;
            this.potted = false;
        }
        
        void update() {
            x += vx;
            y += vy;
            
            // 摩擦力
            vx *= FRICTION;
            vy *= FRICTION;
            
            // 停止微小移动
            if (Math.abs(vx) < 0.05f) vx = 0;
            if (Math.abs(vy) < 0.05f) vy = 0;
            
            // 边界碰撞（考虑桌面偏移）
            if (x - ballRadius < tableX) {
                x = tableX + ballRadius;
                vx = -vx * 0.8f;
            }
            if (x + ballRadius > tableX + tableWidth) {
                x = tableX + tableWidth - ballRadius;
                vx = -vx * 0.8f;
            }
            if (y - ballRadius < tableY) {
                y = tableY + ballRadius;
                vy = -vy * 0.8f;
            }
            if (y + ballRadius > tableY + tableHeight) {
                y = tableY + tableHeight - ballRadius;
                vy = -vy * 0.8f;
            }
        }
    }
}
