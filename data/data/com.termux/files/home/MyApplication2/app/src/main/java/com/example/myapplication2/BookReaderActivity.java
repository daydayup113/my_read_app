package com.example.myapplication2;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.Spine;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.epub.EpubReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class BookReaderActivity extends AppCompatActivity {
    private static final String TAG = "BookReaderActivity";
    
    private TextView titleTextView;
    private TextView contentTextView;
    private Book epubBook;
    private List<String> chapterContents = new ArrayList<>();
    private int currentChapterIndex = 0;
    private float startY;
    
    // 添加一个变量来引用ScrollView
    private View scrollView;
    
    // 添加翻页按钮引用
    private Button previousButton;
    private Button nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置全屏显示
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        setContentView(R.layout.activity_book_reader);
        
        titleTextView = findViewById(R.id.titleTextView);
        contentTextView = findViewById(R.id.contentTextView);
        scrollView = findViewById(R.id.scrollView); // 添加对ScrollView的引用
        
        // 初始化翻页按钮
        previousButton = findViewById(R.id.previousButton);
        nextButton = findViewById(R.id.nextButton);
        
        // 设置按钮点击事件
        previousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                previousPage();
            }
        });
        
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextPage();
            }
        });
        
        // 设置触摸监听器实现上下滑动翻页
        contentTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getY();
                        // 请求父视图不要拦截触摸事件
                        scrollView.getParent().requestDisallowInterceptTouchEvent(true);
                        break;
                    case MotionEvent.ACTION_UP:
                        float endY = event.getY();
                        float deltaY = endY - startY;
                        
                        // 判断滑动方向和距离
                        // 修改滑动阈值从200到100，使翻页更容易触发
                        if (Math.abs(deltaY) > 100) { // 滑动距离阈值
                            if (deltaY > 0) {
                                // 向下滑动，上一页
                                previousPage();
                            } else {
                                // 向上滑动，下一页
                                nextPage();
                            }
                            return true;
                        }
                        // 释放父视图对触摸事件的控制
                        scrollView.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        // 处理取消事件，释放父视图对触摸事件的控制
                        scrollView.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                }
                return false;
            }
        });
        
        String bookPath = getIntent().getStringExtra("book_path");
        if (bookPath != null) {
            loadBook(bookPath);
        }
    }
    
    private void loadBook(String bookPath) {
        try {
            File file = new File(bookPath);
            InputStream inputStream = new FileInputStream(file);
            epubBook = (new EpubReader()).readEpub(inputStream);
            inputStream.close();
            
            // 提取所有章节内容
            extractChapterContents();
            
            // 显示第一章
            currentChapterIndex = 0;
            displayCurrentPage();
        } catch (IOException e) {
            Log.e(TAG, "Error loading book: " + e.getMessage());
            contentTextView.setText("无法加载书籍: " + e.getMessage());
        }
    }
    
    private void extractChapterContents() {
        if (epubBook == null) return;
        
        chapterContents.clear();
        Spine spine = epubBook.getSpine();
        if (spine == null) return;
        
        // 遍历所有章节
        for (SpineReference spineReference : spine.getSpineReferences()) {
            Resource resource = spineReference.getResource();
            try {
                String content = new String(resource.getData(), "UTF-8");
                // 简单提取文本内容（实际应用中可能需要解析HTML）
                content = content.replaceAll("<[^>]*>", "");
                if (!content.trim().isEmpty()) {
                    chapterContents.add(content);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting chapter: " + e.getMessage());
            }
        }
    }
    
    private void displayCurrentPage() {
        if (chapterContents.isEmpty() || currentChapterIndex >= chapterContents.size()) return;
        
        // 显示当前章节内容
        String content = chapterContents.get(currentChapterIndex);
        contentTextView.setText(content);
        
        // 显示书名和当前页码
        if (epubBook != null) {
            String title = epubBook.getTitle();
            if (title != null && !title.isEmpty()) {
                titleTextView.setText(title + " (" + (currentChapterIndex + 1) + "/" + chapterContents.size() + ")");
            } else {
                titleTextView.setText("书籍内容 (" + (currentChapterIndex + 1) + "/" + chapterContents.size() + ")");
            }
        } else {
            titleTextView.setText("书籍内容 (" + (currentChapterIndex + 1) + "/" + chapterContents.size() + ")");
        }
        
        // 每次显示新页面时滚动到顶部
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.scrollTo(0, 0);
            }
        });
        
        // 更新按钮状态
        updateButtonState();
    }
    
    // 更新按钮的可用状态
    private void updateButtonState() {
        previousButton.setEnabled(currentChapterIndex > 0);
        nextButton.setEnabled(currentChapterIndex < chapterContents.size() - 1);
    }
    
    private void nextPage() {
        if (currentChapterIndex < chapterContents.size() - 1) {
            currentChapterIndex++;
            displayCurrentPage();
        } else {
            contentTextView.setText("已经是最后一页了");
        }
    }
    
    private void previousPage() {
        if (currentChapterIndex > 0) {
            currentChapterIndex--;
            displayCurrentPage();
        } else {
            contentTextView.setText("已经是第一页了");
        }
    }
}