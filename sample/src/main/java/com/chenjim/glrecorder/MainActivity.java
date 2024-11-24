package com.chenjim.glrecorder;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    CameraGlView cameraGlView;
    private long startTime = 0;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 先判断有没有权限
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Android VERSION  R OR ABOVE，HAVE MANAGE_EXTERNAL_STORAGE GRANTED!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Android VERSION  R OR ABOVE，NO MANAGE_EXTERNAL_STORAGE GRANTED!", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + this.getPackageName()));
                startActivityForResult(intent, 555);
            }
        }


        cameraGlView = findViewById(R.id.cameraView);

        Button recordButton = findViewById(R.id.btn_record);
        recordButton.setOnTouchListener((View v, MotionEvent event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    cameraGlView.startRecord();
                    startTime = SystemClock.elapsedRealtime();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (SystemClock.elapsedRealtime() - startTime < 2000) {
                        recordButton.postDelayed(() -> cameraGlView.stopRecord(), 1500);
                    } else {
                        cameraGlView.stopRecord();
                    }
                    break;
                default:
                    break;
            }
            return true;
        });

    }
}
