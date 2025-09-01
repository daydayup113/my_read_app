package com.example.myapplication2;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView textView = findViewById(R.id.textView);
        Button manageBooksButton = findViewById(R.id.manageBooksButton);

        // 示例文本
        textView.setText("欢迎使用EPUB阅读器！\n\n点击下方按钮管理您的书籍。");

        manageBooksButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BookManagerActivity.class);
            startActivity(intent);
        });
    }
}