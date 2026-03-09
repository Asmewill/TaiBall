package com.openclaw.taiball;

import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    
    private GameView gameView;
    private TextView scoreText;
    private Button resetButton;
    private Vibrator vibrator;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        
        gameView = findViewById(R.id.gameView);
        scoreText = findViewById(R.id.scoreText);
        resetButton = findViewById(R.id.resetButton);
        
        // 设置分数更新回调
        gameView.setScoreListener(new GameView.ScoreListener() {
            @Override
            public void onScoreUpdate(int score) {
                scoreText.setText("得分：" + score);
            }
            
            @Override
            public void onGameWin() {
                vibrate(500);
                showGameOverDialog("🎉 恭喜你获胜！", true);
            }
            
            @Override
            public void onGameLose() {
                vibrate(200);
                showGameOverDialog("😢 游戏结束！8 号球过早入袋", false);
            }
        });
        
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gameView.resetGame();
                scoreText.setText("得分：0");
            }
        });
    }
    
    private void vibrate(long milliseconds) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }
    
    private void showGameOverDialog(String message, final boolean isWin) {
        new AlertDialog.Builder(this)
            .setTitle(isWin ? "胜利！" : "游戏结束")
            .setMessage(message + "\n当前得分：" + gameView.getScore())
            .setPositiveButton("再来一局", null)
            .setNegativeButton("退出", null)
            .setOnDismissListener(null)
            .show();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        gameView.pauseGame();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        gameView.resumeGame();
    }
}
