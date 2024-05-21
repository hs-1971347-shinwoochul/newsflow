// SplashActivity.java
package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DISPLAY_LENGTH = 1000; // 4 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Delay for SPLASH_DISPLAY_LENGTH milliseconds, then start MainActivity
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Start MainActivity
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                // Close SplashActivity
                finish();
            }
        }, SPLASH_DISPLAY_LENGTH);
    }
}
