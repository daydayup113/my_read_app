package com.example.myapplication2;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color; // 添加缺失的Color类导入
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Spine;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.domain.TableOfContents;
import nl.siegmann.epublib.epub.EpubReader;

public class ReadingActivity extends AppCompatActivity {
    private static final String TAG = "ReadingActivity";
    
    // 添加动画持续时间常量
    private static final int ANIMATION_DURATION = 300;
    
    // 添加翻页按钮动画常量
    private static final int BUTTON_ANIMATION_DURATION = 200;
    
    private static final String PREFS_NAME = "ReadingProgress";
    private static final String FONT_SIZE_PREF = "fontSize";
    private static final String LINE_SPACING_PREF = "lineSpacing";
    private static final String LETTER_SPACING_PREF = "letterSpacing";
    private static final String BACKGROUND_COLOR_PREF = "backgroundColor"; // 添加背景色偏好键
    private static final String TEXT_COLOR_PREF = "textColor"; // 添加字体颜色偏好键
    private ScrollView contentScrollView;
    private TextView contentTextView;
    private View menuLayer;
    private View fontSettingsLayer;
    private ImageButton backButton;
    private ImageButton previousPageButton;
    private ImageButton nextPageButton;
    private ImageButton tocButton;
    private ImageButton settingsButton;
    private ImageButton backgroundButton; // 添加背景色按钮变量
    private boolean isMenuVisible = false;
    private Book epubBook;
    private String bookTitle;
    private Uri bookUri;
    private int currentPage = 0;
    private List<SpineReference> spineReferences;
    private SharedPreferences sharedPreferences;
    
    // 添加菜单视图引用
    private CardView topMenu;
    private CardView bottomMenu;
    
    // 字体设置相关变量
    private float currentTextSize = 18f; // 默认字体大小
    private float currentLineSpacing = 4f; // 默认行距
    private float currentLetterSpacing = 0f; // 默认字距
    private int currentBackgroundColor = 0xFFADD8E6; // 默认背景色 (浅蓝色)
    private int currentTextColor = 0xFF000000; // 默认字体颜色 (黑色)
    
    // 字体设置层中的控件
    private ImageButton btnFontSizeDecrease;
    private ImageButton btnFontSizeIncrease;
    private ImageButton btnLineSpacingDecrease;
    private ImageButton btnLineSpacingIncrease;
    private ImageButton btnLetterSpacingDecrease;
    private ImageButton btnLetterSpacingIncrease;
    private TextView tvFontSize;
    private TextView tvLineSpacing;
    private TextView tvLetterSpacing;
    
    // 添加ActivityResultLauncher来处理目录页面的返回结果
    private ActivityResultLauncher<Intent> tocActivityResultLauncher;
    
    private List<String> txtPages; // 存储TXT文件分页后的内容
    private int currentTxtPage = 0; // 当前TXT文件页码

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置沉浸式状态栏
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
        
        // 设置状态栏和导航栏的背景为透明
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }
        
        setContentView(R.layout.activity_reading);
        Log.d(TAG, "onCreate: ReadingActivity started");

        // 初始化ActivityResultLauncher
        initActivityResultLauncher();

        // 获取传递的书籍信息
        String uriString = getIntent().getStringExtra("book_uri");
        bookTitle = getIntent().getStringExtra("book_title");
        if (uriString != null) {
            bookUri = Uri.parse(uriString);
            // 请求持久的URI权限
            try {
                getContentResolver().takePersistableUriPermission(bookUri, 
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException e) {
                Log.w(TAG, "无法获取URI权限: " + e.getMessage());
            }
        }
        Log.d(TAG, "onCreate: bookUri=" + bookUri + ", bookTitle=" + bookTitle);

        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initViews();
        setupClickListeners();
        
        // 恢复用户的字体设置和背景色
        restoreUserSettings();
        restoreBackgroundColor();
        
        loadBookContent();
        
        // 处理返回键事件
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Log.d(TAG, "handleOnBackPressed: isMenuVisible=" + isMenuVisible);
                if (isMenuVisible) {
                    hideMenu();
                } else if (fontSettingsLayer.getVisibility() == View.VISIBLE) {
                    hideFontSettingsLayer();
                } else {
                    finish();
                }
            }
        });
    }

    private void initActivityResultLauncher() {
        tocActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            int chapterPosition = data.getIntExtra("chapter_position", 0);
                            String chapterTitle = data.getStringExtra("chapter_title");
                            Log.d(TAG, "onActivityResult: chapterPosition=" + chapterPosition + ", chapterTitle=" + chapterTitle);

                            // 跳转到指定章节
                            goToChapter(chapterPosition);
                        }
                    }
                });
    }

    private void initViews() {
        Log.d(TAG, "initViews: Initializing views");
        contentScrollView = findViewById(R.id.contentScrollView);
        contentTextView = findViewById(R.id.contentTextView);
        menuLayer = findViewById(R.id.menuLayer);
        fontSettingsLayer = findViewById(R.id.fontSettingsLayer);
        backButton = findViewById(R.id.backButton);
        previousPageButton = (ImageButton) findViewById(R.id.previousPageButton);
        nextPageButton = (ImageButton) findViewById(R.id.nextPageButton);
        tocButton = findViewById(R.id.tocButton);
        settingsButton = findViewById(R.id.settingsButton);
        backgroundButton = findViewById(R.id.backgroundButton); // 初始化背景色按钮
        
        // 初始化字体设置层中的控件
        if (fontSettingsLayer != null) {
            btnFontSizeDecrease = fontSettingsLayer.findViewById(R.id.btn_font_size_decrease);
            btnFontSizeIncrease = fontSettingsLayer.findViewById(R.id.btn_font_size_increase);
            btnLineSpacingDecrease = fontSettingsLayer.findViewById(R.id.btn_line_spacing_decrease);
            btnLineSpacingIncrease = fontSettingsLayer.findViewById(R.id.btn_line_spacing_increase);
            btnLetterSpacingDecrease = fontSettingsLayer.findViewById(R.id.btn_letter_spacing_decrease);
            btnLetterSpacingIncrease = fontSettingsLayer.findViewById(R.id.btn_letter_spacing_increase);
            tvFontSize = fontSettingsLayer.findViewById(R.id.tv_font_size);
            tvLineSpacing = fontSettingsLayer.findViewById(R.id.tv_line_spacing);
            tvLetterSpacing = fontSettingsLayer.findViewById(R.id.tv_letter_spacing);
        }
        
        // 初始化菜单层为隐藏状态
        menuLayer.setVisibility(View.GONE);
        isMenuVisible = false;
        
        // 初始化字体设置层为隐藏状态
        fontSettingsLayer.setVisibility(View.GONE);
        
        // 设置滚动监听，用于检测是否需要加载更多内容
        // 注意：setOnScrollChangeListener需要API 23及以上
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            contentScrollView.setOnScrollChangeListener((View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) -> {
                // 可以在这里实现滚动到底部时加载更多内容的逻辑
            });
        }
        
        // 初始化菜单视图引用
        topMenu = findViewById(R.id.topMenu);
        bottomMenu = findViewById(R.id.bottomMenu);
    }
    
    // 恢复用户设置
    private void restoreUserSettings() {
        Log.d(TAG, "restoreUserSettings: Restoring user settings");
        currentTextSize = sharedPreferences.getFloat(FONT_SIZE_PREF, 18f);
        currentLineSpacing = sharedPreferences.getFloat(LINE_SPACING_PREF, 4f);
        currentLetterSpacing = sharedPreferences.getFloat(LETTER_SPACING_PREF, 0f);
        currentBackgroundColor = sharedPreferences.getInt(BACKGROUND_COLOR_PREF, 0xFFADD8E6);
        currentTextColor = sharedPreferences.getInt(TEXT_COLOR_PREF, 0xFF000000);
        
        // 应用设置到文本视图
        contentTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize);
        contentTextView.setLineSpacing(
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, currentLineSpacing, getResources().getDisplayMetrics()), 
            1f);

        contentTextView.setLetterSpacing(currentLetterSpacing);
        contentTextView.setTextColor(currentTextColor); // 应用字体颜色

        // 应用背景色
        contentScrollView.setBackgroundColor(currentBackgroundColor);
        
        Log.d(TAG, "restoreUserSettings: Font size=" + currentTextSize + 
              ", Line spacing=" + currentLineSpacing + 
              ", Letter spacing=" + currentLetterSpacing +
              ", Background color=" + Integer.toHexString(currentBackgroundColor) +
              ", Text color=" + Integer.toHexString(currentTextColor));
    }
    
    // 保存用户设置
    private void saveUserSettings() {
        Log.d(TAG, "saveUserSettings: Saving user settings");
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(FONT_SIZE_PREF, currentTextSize);
        editor.putFloat(LINE_SPACING_PREF, currentLineSpacing);
        editor.putFloat(LETTER_SPACING_PREF, currentLetterSpacing);
        editor.putInt(BACKGROUND_COLOR_PREF, currentBackgroundColor);
        editor.putInt(TEXT_COLOR_PREF, currentTextColor);
        editor.apply();
        Log.d(TAG, "saveUserSettings: Settings saved");
    }
    
    private void setupClickListeners() {
        Log.d(TAG, "setupClickListeners: Setting up click listeners");
        
        // 点击内容区域切换菜单显示/隐藏
        contentScrollView.setOnClickListener(v -> {
            Log.d(TAG, "contentScrollView clicked: toggling menu");
            toggleMenu();
        });

        // 点击中间区域显示菜单 - 使用触摸事件检测精确点击
        View centerClickArea = findViewById(R.id.centerClickArea);
        centerClickArea.setOnTouchListener(new View.OnTouchListener() {
            private static final int CLICK_THRESHOLD = 20; // 点击和滑动的阈值
            private float startX, startY;
            private long startTime;
            private boolean isScrolling = false;

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 获取原始事件的pointer index和pointer id
                int action = event.getActionMasked();
                int pointerIndex = event.getActionIndex();
                int pointerId = event.getPointerId(pointerIndex);

                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX(pointerIndex);
                        startY = event.getY(pointerIndex);
                        startTime = System.currentTimeMillis();
                        isScrolling = false;
                        return true; // 消费DOWN事件，确保能接收到后续事件
                        
                    case MotionEvent.ACTION_MOVE:
                        // 计算移动距离
                        float moveX = Math.abs(event.getX(pointerIndex) - startX);
                        float moveY = Math.abs(event.getY(pointerIndex) - startY);
                        
                        // 如果移动距离超过阈值，认为是滑动操作
                        if ((moveX > CLICK_THRESHOLD || moveY > CLICK_THRESHOLD) && !isScrolling) {
                            isScrolling = true;
                            // 创建一个新的MotionEvent，确保pointer index为0
                            MotionEvent scrollEvent = MotionEvent.obtain(
                                event.getDownTime(),
                                event.getEventTime(),
                                MotionEvent.ACTION_DOWN,
                                startX,
                                startY,
                                event.getMetaState()
                            );
                            // 将事件传递给ScrollView
                            contentScrollView.dispatchTouchEvent(scrollEvent);
                            scrollEvent.recycle();
                        }
                        
                        // 如果已经在滚动状态，则继续传递MOVE事件
                        if (isScrolling) {
                            // 创建一个新的MotionEvent，确保pointer index为0
                            MotionEvent scrollEvent = MotionEvent.obtain(
                                event.getDownTime(),
                                event.getEventTime(),
                                MotionEvent.ACTION_MOVE,
                                event.getX(pointerIndex),
                                event.getY(pointerIndex),
                                event.getMetaState()
                            );
                            contentScrollView.dispatchTouchEvent(scrollEvent);
                            scrollEvent.recycle();
                            return true; // 消费MOVE事件
                        }
                        
                        return false; // 不消费MOVE事件
                        
                    case MotionEvent.ACTION_UP:
                        // 如果在滚动状态，则传递UP事件
                        if (isScrolling) {
                            MotionEvent scrollEvent = MotionEvent.obtain(
                                event.getDownTime(),
                                event.getEventTime(),
                                MotionEvent.ACTION_UP,
                                event.getX(pointerIndex),
                                event.getY(pointerIndex),
                                event.getMetaState()
                            );
                            contentScrollView.dispatchTouchEvent(scrollEvent);
                            scrollEvent.recycle();
                            isScrolling = false;
                            return true; // 消费UP事件
                        }
                        
                        // 处理点击事件
                        long endTime = System.currentTimeMillis();
                        float endX = event.getX(pointerIndex);
                        float endY = event.getY(pointerIndex);
                        
                        // 计算移动距离
                        float deltaX = Math.abs(endX - startX);
                        float deltaY = Math.abs(endY - startY);
                        
                        // 判断是否为点击事件
                        // 条件：移动距离小于阈值 且 时间较短
                        if (deltaX < CLICK_THRESHOLD && deltaY < CLICK_THRESHOLD && 
                            (endTime - startTime) < 300) {
                            Log.d(TAG, "centerClickArea touched: showing menu");
                            showMenu();
                            return true; // 消费点击事件
                        }
                        return true; // 消费UP事件
                        
                    case MotionEvent.ACTION_CANCEL:
                        if (isScrolling) {
                            MotionEvent scrollEvent = MotionEvent.obtain(
                                event.getDownTime(),
                                event.getEventTime(),
                                MotionEvent.ACTION_CANCEL,
                                event.getX(pointerIndex),
                                event.getY(pointerIndex),
                                event.getMetaState()
                            );
                            contentScrollView.dispatchTouchEvent(scrollEvent);
                            scrollEvent.recycle();
                            isScrolling = false;
                        }
                        return true; // 消费CANCEL事件
                }
                return false; // 默认不消费事件
            }
        });

        // 点击菜单层中间区域隐藏菜单
        View menuCenterArea = findViewById(R.id.menuCenterArea);
        menuCenterArea.setOnClickListener(v -> {
            Log.d(TAG, "menuCenterArea clicked: hiding menu");
            hideMenu();
        });
        
        // 点击字体设置层半透明遮罩隐藏字体设置层
        View fontSettingsOverlay = fontSettingsLayer.findViewById(R.id.fontSettingsOverlay);
        if (fontSettingsOverlay != null) {
            fontSettingsOverlay.setOnClickListener(v -> {
                Log.d(TAG, "fontSettingsOverlay clicked: hiding font settings layer");
                hideFontSettingsLayer();
            });
        }

        // 返回按钮
        backButton.setOnClickListener(v -> {
            Log.d(TAG, "backButton clicked");
            // 直接返回到书籍列表页面
            finish();
        });

        // 目录按钮
        tocButton.setOnClickListener(v -> {
            Log.d(TAG, "tocButton clicked");
            openTableOfContents();
        });
        
        // 设置按钮
        settingsButton.setOnClickListener(v -> {
            Log.d(TAG, "settingsButton clicked");
            // 显示字体设置层
            showFontSettingsLayer();
        });
        
        // 背景色按钮
        backgroundButton.setOnClickListener(v -> {
            Log.d(TAG, "backgroundButton clicked");
            showBackgroundColorDialog();
        });
        
        // 字体设置层中的按钮点击事件
        if (btnFontSizeDecrease != null) {
            btnFontSizeDecrease.setOnClickListener(v -> decreaseFontSize());
        }
        
        if (btnFontSizeIncrease != null) {
            btnFontSizeIncrease.setOnClickListener(v -> increaseFontSize());
        }
        
        if (btnLineSpacingDecrease != null) {
            btnLineSpacingDecrease.setOnClickListener(v -> decreaseLineSpacing());
        }
        
        if (btnLineSpacingIncrease != null) {
            btnLineSpacingIncrease.setOnClickListener(v -> increaseLineSpacing());
        }
        
        if (btnLetterSpacingDecrease != null) {
            btnLetterSpacingDecrease.setOnClickListener(v -> decreaseLetterSpacing());
        }
        
        if (btnLetterSpacingIncrease != null) {
            btnLetterSpacingIncrease.setOnClickListener(v -> increaseLetterSpacing());
        }
        
        // 翻页按钮
        previousPageButton.setOnClickListener(v -> {
            Log.d(TAG, "previousPageButton clicked");
            previousPage();
        });
        nextPageButton.setOnClickListener(v -> {
            Log.d(TAG, "nextPageButton clicked");
            nextPage();
        });
    }

    @SuppressLint("SetTextI18n")
    private void loadBookContent() {
        Log.d(TAG, "loadBookContent: Starting to load book content");
        // 显示加载提示
        contentTextView.setText("加载中");
        
        new Thread(() -> {
            try {
                // 检查文件扩展名以确定文件类型
                String fileName = getFileNameFromUri(bookUri);
                if (fileName != null && fileName.toLowerCase().endsWith(".txt")) {
                    // 处理TXT文件
                    loadTxtBook();
                } else {
                    // 处理EPUB文件（默认）
                    epubBook = loadEpubBook();
                    if (epubBook != null) {
                        Log.d(TAG, "loadBookContent: EPUB book loaded successfully");
                        // 获取书籍的spine references
                        Spine spine = epubBook.getSpine();
                        spineReferences = spine.getSpineReferences();
                        Log.d(TAG, "loadBookContent: spineReferences size=" + spineReferences.size());
                        
                        // 保存总章节数
                        saveTotalChapters(spineReferences.size());
                        
                        // 恢复阅读进度
                        int savedPage = getSavedProgress();
                        Log.d(TAG, "loadBookContent: savedPage=" + savedPage);
                        // 加载保存的页面内容或第一页内容
                        loadPageContent(savedPage, true); // 恢复进度时保持滚动位置
                    } else {
                        Log.e(TAG, "loadBookContent: Failed to load EPUB book");
                        runOnUiThread(() -> {
                            contentTextView.setText("无法加载书籍内容");
                            Toast.makeText(ReadingActivity.this, "加载书籍失败", Toast.LENGTH_LONG).show();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "加载书籍时出错", e);
                runOnUiThread(() -> {
                    contentTextView.setText("加载书籍时出错: " + e.getMessage());
                    Toast.makeText(ReadingActivity.this, "加载书籍时出错", Toast.LENGTH_LONG).show();
                });
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "内存不足，无法加载书籍", e);
                runOnUiThread(() -> {
                    contentTextView.setText("内存不足，无法加载书籍");
                    Toast.makeText(ReadingActivity.this, "内存不足，无法加载书籍", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    // 加载TXT格式的书籍
    private void loadTxtBook() {
        Log.d(TAG, "loadTxtBook: Loading TXT book from URI: " + bookUri);
        try (InputStream inputStream = getContentResolver().openInputStream(bookUri)) {
            if (inputStream != null) {
                // 自动检测编码并正确读取TXT文件
                String charsetName = detectCharset(inputStream);
                Log.d(TAG, "loadTxtBook: Detected charset: " + charsetName);
                
                // 重新打开输入流，因为detectCharset已经读取了部分数据
                try (InputStream newInputStream = getContentResolver().openInputStream(bookUri)) {
                    if (newInputStream != null) {
                        // 分块读取大文件，避免OOM
                        txtPages = new ArrayList<>();
                        StringBuilder currentPage = new StringBuilder();
                        
                        // 使用BufferedReader按行读取，避免大字符串操作
                        BufferedReader reader = new BufferedReader(new InputStreamReader(newInputStream, charsetName));
                        String line;
                        long totalChars = 0;
                        
                        while ((line = reader.readLine()) != null) {
                            currentPage.append(line).append("\n");
                            totalChars += line.length() + 1; // +1 for newline character
                            
                            // 每读取约20000个字符就分一页（避免单页内容过多）
                            if (totalChars > 20000) {
                                txtPages.add(currentPage.toString());
                                currentPage = new StringBuilder();
                                totalChars = 0; // 重置计数器
                            }
                        }
                        reader.close();
                        
                        // 添加最后一页（如果有内容）
                        if (currentPage.length() > 0) {
                            txtPages.add(currentPage.toString());
                        }
                        
                        Log.d(TAG, "loadTxtBook: TXT book loaded successfully, pages: " + txtPages.size());
                        
                        // 在主线程中更新UI
                        runOnUiThread(() -> {
                            if (!txtPages.isEmpty()) {
                                currentTxtPage = 0;
                                displayTxtPage();
                                updateTxtPageButtons();
                            } else {
                                contentTextView.setText("文件内容为空");
                            }
                            
                            // 保存总章节数
                            saveTotalChapters(txtPages.size());
                        });
                    } else {
                        throw new Exception("无法重新打开输入流");
                    }
                }
            } else {
                Log.e(TAG, "loadTxtBook: Failed to open input stream");
                runOnUiThread(() -> {
                    contentTextView.setText("无法打开书籍文件");
                    Toast.makeText(ReadingActivity.this, "无法打开书籍文件", Toast.LENGTH_LONG).show();
                });
            }
        } catch (SecurityException e) {
            Log.e(TAG, "没有权限访问TXT文件: " + e.getMessage(), e);
            runOnUiThread(() -> {
                Toast.makeText(ReadingActivity.this, "没有权限访问TXT文件，请重新选择书籍", Toast.LENGTH_LONG).show();
            });
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "内存不足，无法加载TXT文件", e);
            runOnUiThread(() -> {
                contentTextView.setText("内存不足，无法加载书籍。请尝试阅读较小的文件。");
                Toast.makeText(ReadingActivity.this, "内存不足，无法加载书籍", Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            Log.e(TAG, "解析TXT文件时出错", e);
            runOnUiThread(() -> {
                Toast.makeText(ReadingActivity.this, "解析TXT文件时出错: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }
    
    // 检测TXT文件的字符编码
    private String detectCharset(InputStream inputStream) {
        try {
            // 读取文件开头的部分字节用于编码检测
            byte[] headBuffer = new byte[4096];
            int bytesRead = inputStream.read(headBuffer);
            
            if (bytesRead == -1) {
                // 文件为空，使用默认编码
                return "UTF-8";
            }
            
            // 检查BOM标记
            if (bytesRead >= 3 && headBuffer[0] == (byte)0xEF && headBuffer[1] == (byte)0xBB && headBuffer[2] == (byte)0xBF) {
                // UTF-8 BOM
                return "UTF-8";
            } else if (bytesRead >= 2 && headBuffer[0] == (byte)0xFF && headBuffer[1] == (byte)0xFE) {
                // UTF-16 LE BOM
                return "UTF-16LE";
            } else if (bytesRead >= 2 && headBuffer[0] == (byte)0xFE && headBuffer[1] == (byte)0xFF) {
                // UTF-16 BE BOM
                return "UTF-16BE";
            } else if (bytesRead >= 4 && headBuffer[0] == (byte)0xFF && headBuffer[1] == 0x00 && headBuffer[2] == 0x00 && headBuffer[3] == 0x00) {
                // UTF-32 BE BOM (FF 00 00 00)
                return "UTF-32BE";
            } else if (bytesRead >= 4 && headBuffer[0] == (byte)0x00 && headBuffer[1] == 0x00 && headBuffer[2] == 0x00 && headBuffer[3] == (byte)0xFF) {
                // UTF-32 LE BOM (00 00 00 FF)
                return "UTF-32LE";
            } else if (bytesRead >= 4 && headBuffer[0] == (byte)0x00 && headBuffer[1] == 0x00 && headBuffer[2] == (byte)0xFE && headBuffer[3] == (byte)0xFF) {
                // UTF-32 BE BOM (00 00 FE FF)
                return "UTF-32BE";
            } else if (bytesRead >= 4 && headBuffer[0] == (byte)0xFF && headBuffer[1] == (byte)0xFE && headBuffer[2] == 0x00 && headBuffer[3] == 0x00) {
                // UTF-32 LE BOM (FF FE 00 00)
                return "UTF-32LE";
            }
            
            // 检查是否可能为UTF-8（无BOM）
            if (isUtf8(headBuffer, bytesRead)) {
                return "UTF-8";
            }
            
            // 检查是否为GBK编码（中文）
            if (isGbk(headBuffer, bytesRead)) {
                return "GBK";
            }
            
            // 默认使用UTF-8
            return "UTF-8";
        } catch (Exception e) {
            Log.e(TAG, "detectCharset: Error detecting charset, using UTF-8 as default", e);
            return "UTF-8";
        }
    }
    
    // 简单检测是否为UTF-8编码
    private boolean isUtf8(byte[] buffer, int length) {
        int i = 0;
        while (i < length) {
            // 检查单字节字符 (0xxxxxxx)
            if ((buffer[i] & 0x80) == 0) {
                i++;
            }
            // 检查双字节字符 (110xxxxx 10xxxxxx)
            else if ((buffer[i] & 0xE0) == 0xC0) {
                if (i + 1 >= length || (buffer[i + 1] & 0xC0) != 0x80) {
                    return false;
                }
                i += 2;
            }
            // 检查三字节字符 (1110xxxx 10xxxxxx 10xxxxxx)
            else if ((buffer[i] & 0xF0) == 0xE0) {
                if (i + 2 >= length || (buffer[i + 1] & 0xC0) != 0x80 || (buffer[i + 2] & 0xC0) != 0x80) {
                    return false;
                }
                i += 3;
            }
            // 检查四字节字符 (11110xxx 10xxxxxx 10xxxxxx 10xxxxxx)
            else if ((buffer[i] & 0xF8) == 0xF0) {
                if (i + 3 >= length || (buffer[i + 1] & 0xC0) != 0x80 || (buffer[i + 2] & 0xC0) != 0x80 || (buffer[i + 3] & 0xC0) != 0x80) {
                    return false;
                }
                i += 4;
            } else {
                // 不符合UTF-8编码规则
                return false;
            }
        }
        return true;
    }
    
    // 简单检测是否可能为GBK编码
    private boolean isGbk(byte[] buffer, int length) {
        try {
            // 尝试用GBK解码一部分数据，看是否会产生异常
            String str = new String(buffer, 0, Math.min(length, 1024), "GBK");
            // 检查解码后的字符串是否包含明显的乱码字符
            // 这里使用一个简单的启发式方法：如果解码后的字符串中包含较多的问号或方块字符，可能不是GBK
            int invalidCharCount = 0;
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                if (c == 0xfffd || c < 32 && c != '\n' && c != '\r' && c != '\t') {
                    invalidCharCount++;
                }
            }
            // 如果无效字符比例小于30%，则可能是GBK编码
            return ((double) invalidCharCount / str.length()) < 0.3;
        } catch (Exception e) {
            return false;
        }
    }
    
    // 显示TXT文件当前页
    private void displayTxtPage() {
        if (txtPages != null && !txtPages.isEmpty() && currentTxtPage >= 0 && currentTxtPage < txtPages.size()) {
            contentTextView.setText(txtPages.get(currentTxtPage));
            Log.d(TAG, "displayTxtPage: Displaying page " + (currentTxtPage + 1) + "/" + txtPages.size());
        }
    }
    
    // 更新TXT文件翻页按钮状态
    private void updateTxtPageButtons() {
        if (txtPages == null || txtPages.isEmpty()) return;
        
        // 检查按钮是否需要显示
        boolean shouldShowButtons = !isMenuVisible && fontSettingsLayer.getVisibility() != View.VISIBLE;
        
        if (shouldShowButtons) {
            // 显示翻页按钮并添加动画效果
            if (previousPageButton.getVisibility() != View.VISIBLE) {
                previousPageButton.setVisibility(View.VISIBLE);
                animateButtonAppear(previousPageButton);
            }
            
            if (nextPageButton.getVisibility() != View.VISIBLE) {
                nextPageButton.setVisibility(View.VISIBLE);
                animateButtonAppear(nextPageButton);
            }
            
            // 更新按钮启用状态
            previousPageButton.setEnabled(currentTxtPage > 0);
            nextPageButton.setEnabled(currentTxtPage < txtPages.size() - 1);
        } else {
            // 隐藏翻页按钮
            previousPageButton.setVisibility(View.GONE);
            nextPageButton.setVisibility(View.GONE);
        }
    }
    
    // TXT文件上一页
    private void txtPreviousPage() {
        if (txtPages != null && currentTxtPage > 0) {
            currentTxtPage--;
            displayTxtPage();
            updateTxtPageButtons();
            contentScrollView.scrollTo(0, 0); // 滚动到顶部
            
            // 保存阅读进度
            saveProgress(currentTxtPage);
        } else {
            Toast.makeText(this, "已经是第一页", Toast.LENGTH_SHORT).show();
        }
    }
    
    // TXT文件下一页
    private void txtNextPage() {
        if (txtPages != null && currentTxtPage < txtPages.size() - 1) {
            currentTxtPage++;
            displayTxtPage();
            updateTxtPageButtons();
            contentScrollView.scrollTo(0, 0); // 滚动到顶部
            
            // 保存阅读进度
            saveProgress(currentTxtPage);
        } else {
            Toast.makeText(this, "已经是最后一页", Toast.LENGTH_SHORT).show();
        }
    }
    
    // 从Uri获取文件名
    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        
        // 尝试通过ContentResolver获取真实文件名
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // 如果无法获取到文件名，则使用uri的最后一段
        if (fileName == null) {
            fileName = uri.getLastPathSegment();
        }
        
        return fileName;
    }
    
    private Book loadEpubBook() {
        Log.d(TAG, "loadEpubBook: Loading EPUB book from URI: " + bookUri);
        try (InputStream epubInputStream = getContentResolver().openInputStream(bookUri)) {
            return new EpubReader().readEpub(epubInputStream);
        } catch (SecurityException e) {
            Log.e(TAG, "没有权限访问EPUB文件: " + e.getMessage(), e);
            runOnUiThread(() -> {
                Toast.makeText(ReadingActivity.this, "没有权限访问EPUB文件，请重新选择书籍", Toast.LENGTH_LONG).show();
            });
            return null;
        } catch (Exception e) {
            Log.e(TAG, "解析EPUB文件时出错", e);
            runOnUiThread(() -> {
                Toast.makeText(ReadingActivity.this, "解析EPUB文件时出错: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
            return null;
        }
    }
    
    @SuppressLint("SetTextI18n")
    private void loadPageContent(int pageIndex, boolean preserveScrollPosition) {
        Log.d(TAG, "loadPageContent: pageIndex=" + pageIndex + ", preserveScrollPosition=" + preserveScrollPosition);
        if (spineReferences == null || pageIndex < 0 || pageIndex >= spineReferences.size()) {
            Log.w(TAG, "loadPageContent: Invalid page index or spineReferences null");
            runOnUiThread(() -> {
                contentTextView.setText("没有更多内容");
            });
            return;
        }
        
        new Thread(() -> {
            try {
                SpineReference spineReference = spineReferences.get(pageIndex);
                String content = new String(spineReference.getResource().getData());
                Log.d(TAG, "loadPageContent: Content loaded, length=" + content.length());
                
                runOnUiThread(() -> {
                    // 使用Html.fromHtml来正确解析HTML内容
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        contentTextView.setText(Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY));
                    } else {
                        contentTextView.setText(Html.fromHtml(content));
                    }
                    currentPage = pageIndex;
                    updatePageButtons();
                    
                    // 只有在恢复进度时才保持滚动位置，翻页时滚动到顶部
                    if (!preserveScrollPosition) {
                        Log.d(TAG, "loadPageContent: Scrolling to top");
                        contentScrollView.scrollTo(0, 0);
                    }
                });
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "内存不足，无法加载章节", e);
                runOnUiThread(() -> {
                    contentTextView.setText("内存不足，无法加载章节");
                    Toast.makeText(ReadingActivity.this, "内存不足，无法加载章节", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "加载章节时出错", e);
                runOnUiThread(() -> {
                    contentTextView.setText("加载章节时出错: " + e.getMessage());
                    Toast.makeText(ReadingActivity.this, "加载章节时出错", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    // 重载方法，默认翻页时不保持滚动位置
    private void loadPageContent(int pageIndex) {
        loadPageContent(pageIndex, false);
    }

    private void updatePageButtons() {
        if (spineReferences == null) return;
        
        // 检查按钮是否需要显示
        boolean shouldShowButtons = !isMenuVisible && fontSettingsLayer.getVisibility() != View.VISIBLE;
        
        if (shouldShowButtons) {
            // 显示翻页按钮并添加动画效果
            if (previousPageButton.getVisibility() != View.VISIBLE) {
                previousPageButton.setVisibility(View.VISIBLE);
                animateButtonAppear(previousPageButton);
            }
            
            if (nextPageButton.getVisibility() != View.VISIBLE) {
                nextPageButton.setVisibility(View.VISIBLE);
                animateButtonAppear(nextPageButton);
            }
        } else {
            // 隐藏翻页按钮并添加动画效果
            if (previousPageButton.getVisibility() == View.VISIBLE) {
                animateButtonDisappear(previousPageButton);
            }
            
            if (nextPageButton.getVisibility() == View.VISIBLE) {
                animateButtonDisappear(nextPageButton);
            }
        }
        
        // 更新按钮的可用性
        previousPageButton.setEnabled(currentPage > 0);
        nextPageButton.setEnabled(currentPage < spineReferences.size() - 1);
        
        // 根据按钮状态更新图标颜色（禁用状态使用灰色）
        updateButtonColors();
    }
    
    // 显示按钮动画
    private void animateButtonAppear(ImageButton button) {
        AnimationSet animationSet = new AnimationSet(true);
        
        // 缩放动画
        ScaleAnimation scaleAnimation = new ScaleAnimation(
                0f, 1f, // X轴缩放
                0f, 1f, // Y轴缩放
                Animation.RELATIVE_TO_SELF, 0.5f, // 缩放中心X
                Animation.RELATIVE_TO_SELF, 0.5f); // 缩放中心Y
        
        // 渐变动画
        AlphaAnimation alphaAnimation = new AlphaAnimation(0f, 1f);
        
        animationSet.addAnimation(scaleAnimation);
        animationSet.addAnimation(alphaAnimation);
        animationSet.setDuration(BUTTON_ANIMATION_DURATION);
        animationSet.setInterpolator(this, android.R.interpolator.decelerate_cubic);
        
        button.startAnimation(animationSet);
    }
    
    // 隐藏按钮动画
    private void animateButtonDisappear(ImageButton button) {
        AnimationSet animationSet = new AnimationSet(true);
        
        // 缩放动画
        ScaleAnimation scaleAnimation = new ScaleAnimation(
                1f, 0f, // X轴缩放
                1f, 0f, // Y轴缩放
                Animation.RELATIVE_TO_SELF, 0.5f, // 缩放中心X
                Animation.RELATIVE_TO_SELF, 0.5f); // 缩放中心Y
        
        // 渐变动画
        AlphaAnimation alphaAnimation = new AlphaAnimation(1f, 0f);
        
        animationSet.addAnimation(scaleAnimation);
        animationSet.addAnimation(alphaAnimation);
        animationSet.setDuration(BUTTON_ANIMATION_DURATION);
        animationSet.setInterpolator(this, android.R.interpolator.accelerate_cubic);
        
        // 动画结束后隐藏按钮
        animationSet.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            
            @Override
            public void onAnimationRepeat(Animation animation) {}
            
            @Override
            public void onAnimationEnd(Animation animation) {
                button.setVisibility(View.GONE);
            }
        });
        
        button.startAnimation(animationSet);
    }
    
    // 更新按钮颜色状态（ImageButton不需要特殊处理）
    private void updateButtonColors() {
        // ImageButton会自动根据启用状态改变外观，无需额外处理
    }
    
    private void toggleMenu() {
        Log.d(TAG, "toggleMenu: Current state isMenuVisible=" + isMenuVisible);
        if (isMenuVisible) {
            hideMenu();
        } else {
            showMenu();
        }
    }

    private void showMenu() {
        Log.d(TAG, "showMenu: Showing menu");
        menuLayer.setVisibility(View.VISIBLE);
        isMenuVisible = true;
        hidePageButtons();
        
        // 设置书籍标题
        TextView bookTitleView = findViewById(R.id.bookTitle);
        if (bookTitleView != null && bookTitle != null) {
            bookTitleView.setText(bookTitle);
        }
        
        // 添加滑出动画
        // 顶部菜单从上方滑入
        TranslateAnimation topAnimation = new TranslateAnimation(
                TranslateAnimation.RELATIVE_TO_SELF, 0,
                TranslateAnimation.RELATIVE_TO_SELF, 0,
                TranslateAnimation.RELATIVE_TO_SELF, -1.0f,
                TranslateAnimation.RELATIVE_TO_SELF, 0);
        topAnimation.setDuration(ANIMATION_DURATION);
        topAnimation.setFillAfter(true);
        
        // 底部菜单从下方滑入
        TranslateAnimation bottomAnimation = new TranslateAnimation(
                TranslateAnimation.RELATIVE_TO_SELF, 0,
                TranslateAnimation.RELATIVE_TO_SELF, 0,
                TranslateAnimation.RELATIVE_TO_SELF, 1.0f,
                TranslateAnimation.RELATIVE_TO_SELF, 0);
        bottomAnimation.setDuration(ANIMATION_DURATION);
        bottomAnimation.setFillAfter(true);
        
        // 应用动画
        if (topMenu != null) {
            topMenu.startAnimation(topAnimation);
        }
        if (bottomMenu != null) {
            bottomMenu.startAnimation(bottomAnimation);
        }
    }
    
    private void hidePageButtons() {
        Log.d(TAG, "hidePageButtons: Hiding page navigation buttons");
        // 使用动画隐藏按钮
        if (previousPageButton.getVisibility() == View.VISIBLE) {
            animateButtonDisappear(previousPageButton);
        }
        
        if (nextPageButton.getVisibility() == View.VISIBLE) {
            animateButtonDisappear(nextPageButton);
        }
    }

    private void hideMenu() {
        Log.d(TAG, "hideMenu: Hiding menu");
        
        // 添加滑出动画
        TranslateAnimation topAnimation = new TranslateAnimation(
                TranslateAnimation.RELATIVE_TO_SELF, 0,
                TranslateAnimation.RELATIVE_TO_SELF, 0,
                TranslateAnimation.RELATIVE_TO_SELF, 0,
                TranslateAnimation.RELATIVE_TO_SELF, -1.0f);
        topAnimation.setDuration(ANIMATION_DURATION);
        topAnimation.setFillAfter(true);
        
        TranslateAnimation bottomAnimation = new TranslateAnimation(
                TranslateAnimation.RELATIVE_TO_SELF, 0,
                TranslateAnimation.RELATIVE_TO_SELF, 0,
                TranslateAnimation.RELATIVE_TO_SELF, 0,
                TranslateAnimation.RELATIVE_TO_SELF, 1.0f);
        bottomAnimation.setDuration(ANIMATION_DURATION);
        bottomAnimation.setFillAfter(true);
        
        // 动画结束后隐藏菜单层
        bottomAnimation.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
            @Override
            public void onAnimationStart(android.view.animation.Animation animation) {}
            
            @Override
            public void onAnimationRepeat(android.view.animation.Animation animation) {}
            
            @Override
            public void onAnimationEnd(android.view.animation.Animation animation) {
                menuLayer.setVisibility(View.GONE);
                isMenuVisible = false;
                // 显示翻页按钮
                updatePageButtons();
            }
        });
        
        // 应用动画
        if (topMenu != null) {
            topMenu.startAnimation(topAnimation);
        }
        if (bottomMenu != null) {
            bottomMenu.startAnimation(bottomAnimation);
        }
    }

    private void openTableOfContents() {
        Log.d(TAG, "openTableOfContents: bookTitle=" + bookTitle + ", spineReferences size=" + (spineReferences != null ? spineReferences.size() : "null"));
        if (epubBook != null && bookUri != null) {
            TableOfContents tableOfContents = epubBook.getTableOfContents();
            if (tableOfContents != null && !tableOfContents.getTocReferences().isEmpty()) {
                Log.d(TAG, "openTableOfContents: Opening table of contents");
                Intent intent = new Intent(this, TableOfContentsActivity.class);
                intent.putExtra("book_uri", bookUri.toString());
                intent.putExtra("book_title", bookTitle);
                intent.putExtra("current_chapter", currentPage); // 传递当前章节位置
                tocActivityResultLauncher.launch(intent);
                hideMenu();
            } else {
                Toast.makeText(this, "该书籍没有目录信息", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "书籍未加载完成", Toast.LENGTH_SHORT).show();
        }
    }
    
    // 显示字体设置层
    private void showFontSettingsLayer() {
        Log.d(TAG, "showFontSettingsLayer: Showing font settings layer");
        // 隐藏菜单层
        hideMenu();
        
        // 显示字体设置层
        fontSettingsLayer.setVisibility(View.VISIBLE);
        
        // 更新显示的当前设置值
        updateFontSettingsDisplay();
    }
    
    // 隐藏字体设置层
    private void hideFontSettingsLayer() {
        Log.d(TAG, "hideFontSettingsLayer: Hiding font settings layer");
        fontSettingsLayer.setVisibility(View.GONE);
    }
    
    // 更新字体设置显示
    @SuppressLint("SetTextI18n")
    private void updateFontSettingsDisplay() {
        tvFontSize.setText("" + currentTextSize);
        tvLineSpacing.setText(currentLineSpacing + "dp");
        tvLetterSpacing.setText("" + currentLetterSpacing);
    }
    
    // 增大字体
    private void increaseFontSize() {
        currentTextSize += 1f;
        contentTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize);
        updateFontSettingsDisplay();
        saveUserSettings(); // 保存设置
        Log.d(TAG, "increaseFontSize: Text size increased to " + currentTextSize);
    }
    
    // 减小字体
    private void decreaseFontSize() {
        if (currentTextSize > 8f) { // 限制最小字体大小
            currentTextSize -= 1f;
            contentTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize);
            updateFontSettingsDisplay();
            saveUserSettings(); // 保存设置
            Log.d(TAG, "decreaseFontSize: Text size decreased to " + currentTextSize);
        }
    }
    
    // 增大行距
    private void increaseLineSpacing() {
        currentLineSpacing += 1f;
        contentTextView.setLineSpacing(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, currentLineSpacing, getResources().getDisplayMetrics()), 1f);
        updateFontSettingsDisplay();
        saveUserSettings(); // 保存设置
        Log.d(TAG, "increaseLineSpacing: Line spacing increased to " + currentLineSpacing);
    }
    
    // 减小行距
    private void decreaseLineSpacing() {
        if (currentLineSpacing > 0f) { // 限制最小行距
            currentLineSpacing -= 1f;
            contentTextView.setLineSpacing(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, currentLineSpacing, getResources().getDisplayMetrics()), 1f);
            updateFontSettingsDisplay();
            saveUserSettings(); // 保存设置
            Log.d(TAG, "decreaseLineSpacing: Line spacing decreased to " + currentLineSpacing);
        }
    }
    
    // 增大字距
    private void increaseLetterSpacing() {
        currentLetterSpacing += 0.05f;
        contentTextView.setLetterSpacing(currentLetterSpacing);
        updateFontSettingsDisplay();
        saveUserSettings(); // 保存设置
        Log.d(TAG, "increaseLetterSpacing: Letter spacing increased to " + currentLetterSpacing);
    }
    
    // 减小字距
    private void decreaseLetterSpacing() {
        if (currentLetterSpacing > -0.5f) { // 限制最小字距
            currentLetterSpacing -= 0.05f;
            contentTextView.setLetterSpacing(currentLetterSpacing);
            updateFontSettingsDisplay();
            saveUserSettings(); // 保存设置
            Log.d(TAG, "decreaseLetterSpacing: Letter spacing decreased to " + currentLetterSpacing);
        }
    }
    
    // 设置字体设置层中按钮的点击事件
    private void setupFontSettingsClickListeners() {
        btnFontSizeDecrease.setOnClickListener(v -> decreaseFontSize());
        btnFontSizeIncrease.setOnClickListener(v -> increaseFontSize());
        btnLineSpacingDecrease.setOnClickListener(v -> decreaseLineSpacing());
        btnLineSpacingIncrease.setOnClickListener(v -> increaseLineSpacing());
        btnLetterSpacingDecrease.setOnClickListener(v -> decreaseLetterSpacing());
        btnLetterSpacingIncrease.setOnClickListener(v -> increaseLetterSpacing());
    }
    
    // 添加翻页方法
    private void nextPage() {
        Log.d(TAG, "nextPage: currentPage=" + currentPage);
        // 检查当前是否为TXT文件阅读模式
        if (txtPages != null) {
            // TXT文件翻页
            txtNextPage();
        } else if (spineReferences != null && currentPage < spineReferences.size() - 1) {
            loadPageContent(currentPage + 1);
            // 保存阅读进度
            saveProgress(currentPage + 1);
        } else {
            Toast.makeText(this, "已经是最后一页", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void previousPage() {
        Log.d(TAG, "previousPage: currentPage=" + currentPage);
        // 检查当前是否为TXT文件阅读模式
        if (txtPages != null) {
            // TXT文件翻页
            txtPreviousPage();
        } else if (currentPage > 0) {
            loadPageContent(currentPage - 1);
            // 保存阅读进度
            saveProgress(currentPage - 1);
        } else {
            Toast.makeText(this, "已经是第一页", Toast.LENGTH_SHORT).show();
        }
    }
    
    // 跳转到指定章节
    private void goToChapter(int chapterIndex) {
        Log.d(TAG, "goToChapter: chapterIndex=" + chapterIndex);
        if (spineReferences != null && chapterIndex >= 0 && chapterIndex < spineReferences.size()) {
            loadPageContent(chapterIndex);
            // 保存阅读进度
            saveProgress(chapterIndex);
        } else {
            Toast.makeText(this, "无法跳转到指定章节", Toast.LENGTH_SHORT).show();
        }
    }
    
    // 保存阅读进度
    private void saveProgress(int page) {
        Log.d(TAG, "saveProgress: page=" + page);
        if (bookUri != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt(bookUri.toString(), page);
            editor.apply();
            
            // 更新书籍列表中的进度信息
            updateBookProgress(page);
            // 更新MainActivity中的书籍进度信息
            updateBookProgressInMainActivity();
            // 保存最后阅读章节信息到MainActivity
            updateLastChapterInMainActivity(page);
        }
    }
    
    // 更新书籍列表中的进度信息
    private void updateBookProgress(int page) {
        // 在实际应用中，这里应该更新书籍列表中的进度信息
        // 由于当前架构限制，我们只记录日志
        Log.d(TAG, "updateBookProgress: Updating book progress to page " + page);
    }
    
    // 将书籍进度信息更新到MainActivity的SharedPreferences中
    private void updateBookProgressInMainActivity() {
        if (bookUri != null) {
            // 获取MainActivity使用的SharedPreferences
            SharedPreferences mainPrefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = mainPrefs.edit();
            
            // 使用书籍URI作为键，保存当前进度
            editor.putInt(bookUri.toString(), currentPage);
            // 保存总页数
            editor.putInt(bookUri.toString() + "_total", spineReferences != null ? spineReferences.size() : 0);
            
            // 提交保存
            editor.apply();
            
            Log.d(TAG, "updateBookProgressInMainActivity: Updated progress for " + bookUri + " to page " + currentPage);
        }
    }
    
    // 将最后阅读章节信息更新到MainActivity的SharedPreferences中
    private void updateLastChapterInMainActivity(int page) {
        if (bookUri != null && spineReferences != null && page >= 0 && page < spineReferences.size()) {
            try {
                // 获取当前章节标题
                String chapterTitle = spineReferences.get(page).getResource().getTitle();
                if (chapterTitle == null || chapterTitle.isEmpty()) {
                    // 如果章节没有标题，使用章节索引作为标题
                    chapterTitle = "第" + (page + 1) + "章";
                }
                
                // 获取MainActivity使用的SharedPreferences
                SharedPreferences mainPrefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
                SharedPreferences.Editor editor = mainPrefs.edit();
                
                // 保存最后阅读章节
                editor.putString(bookUri.toString() + "_lastChapter", chapterTitle);
                
                // 提交保存
                editor.apply();
                
                Log.d(TAG, "updateLastChapterInMainActivity: Updated last chapter for " + bookUri + " to " + chapterTitle);
            } catch (Exception e) {
                Log.e(TAG, "updateLastChapterInMainActivity: Error getting chapter title", e);
            }
        }
    }
    
    // 保存总章节数
    private void saveTotalChapters(int totalPages) {
        Log.d(TAG, "saveTotalChapters: totalPages=" + totalPages);
        if (bookUri != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt(bookUri.toString() + "_total", totalPages);
            editor.apply();
            
            // 同时更新MainActivity中的总章节数
            updateTotalChaptersInMainActivity(totalPages);
        }
    }
    
    // 将总章节数更新到MainActivity的SharedPreferences中
    private void updateTotalChaptersInMainActivity(int totalPages) {
        if (bookUri != null) {
            try {
                // 获取最后一章的标题
                String finalChapterTitle = "";
                if (spineReferences != null && !spineReferences.isEmpty()) {
                    // 获取最后一章
                    SpineReference lastChapter = spineReferences.get(spineReferences.size() - 1);
                    finalChapterTitle = lastChapter.getResource().getTitle();
                    if (finalChapterTitle == null || finalChapterTitle.isEmpty()) {
                        // 如果章节没有标题，使用章节索引作为标题
                        finalChapterTitle = "第" + spineReferences.size() + "章";
                    }
                }
                
                // 获取MainActivity使用的SharedPreferences
                SharedPreferences mainPrefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
                SharedPreferences.Editor editor = mainPrefs.edit();
                
                // 保存总章节数
                editor.putInt(bookUri.toString() + "_total", totalPages);
                // 保存最后一章信息
                editor.putString(bookUri.toString() + "_finalChapter", finalChapterTitle);
                
                // 提交保存
                editor.apply();
                
                Log.d(TAG, "updateTotalChaptersInMainActivity: Updated total chapters for " + bookUri + " to " + totalPages + ", final chapter: " + finalChapterTitle);
            } catch (Exception e) {
                Log.e(TAG, "updateTotalChaptersInMainActivity: Error getting final chapter title", e);
            }
        }
    }
    
    // 获取总章节数
    private int getTotalChapters() {
        if (bookUri != null) {
            int totalPages = sharedPreferences.getInt(bookUri.toString() + "_total", 0);
            Log.d(TAG, "getTotalChapters: totalPages=" + totalPages);
            return totalPages;
        }
        Log.d(TAG, "getTotalChapters: bookUri is null, returning 0");
        return 0;
    }
    
    // 获取保存的阅读进度
    private int getSavedProgress() {
        if (bookUri != null) {
            int progress = sharedPreferences.getInt(bookUri.toString(), 0);
            Log.d(TAG, "getSavedProgress: progress=" + progress);
            return progress;
        }
        Log.d(TAG, "getSavedProgress: bookUri is null, returning 0");
        return 0;
    }
    
    // 显示背景色选择对话框
    private void showBackgroundColorDialog() {
        Log.d(TAG, "showBackgroundColorDialog: Showing background color dialog");
        // 隐藏菜单层
        hideMenu();
        
        // 使用颜色选择器
        ColorPickerDialog colorPickerDialog = new ColorPickerDialog(this, currentBackgroundColor);
        colorPickerDialog.setOnColorSelectedListener((color, changeTextColor) -> {
            // 更新背景色
            currentBackgroundColor = color;
            updateBackgroundColor();
            
            // 如果需要同时修改字体颜色
            if (changeTextColor) {
                // 根据背景色计算对比度高的字体颜色（简单处理：亮背景用黑色字体，暗背景用白色字体）
                currentTextColor = calculateContrastColor(color);
                updateTextColor();
            }
            
            // 保存背景色设置
            saveBackgroundColor();
            Log.d(TAG, "Background color changed to: #" + Integer.toHexString(color));
        });
        colorPickerDialog.show();
    }
    
    // 根据背景色计算对比度高的字体颜色
    private int calculateContrastColor(int backgroundColor) {
        // 计算亮度
        int red = Color.red(backgroundColor);
        int green = Color.green(backgroundColor);
        int blue = Color.blue(backgroundColor);
        
        // 使用相对亮度公式计算亮度
        double luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255;
        
        // 如果亮度大于0.5，则使用黑色字体，否则使用白色字体
        return luminance > 0.5 ? Color.BLACK : Color.WHITE;
    }
    
    // 更新背景色
    private void updateBackgroundColor() {
        Log.d(TAG, "updateBackgroundColor: Updating background color to " + String.format("#%06X", (0xFFFFFF & currentBackgroundColor)));
        contentScrollView.setBackgroundColor(currentBackgroundColor);
        contentTextView.setBackgroundColor(currentBackgroundColor);
    }
    
    // 更新字体颜色
    private void updateTextColor() {
        Log.d(TAG, "updateTextColor: Updating text color to " + String.format("#%06X", (0xFFFFFF & currentTextColor)));
        contentTextView.setTextColor(currentTextColor);
    }
    
    // 保存背景色设置
    private void saveBackgroundColor() {
        Log.d(TAG, "saveBackgroundColor: Saving background color");
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(BACKGROUND_COLOR_PREF, currentBackgroundColor);
        editor.putInt(TEXT_COLOR_PREF, currentTextColor);
        editor.apply();
        Log.d(TAG, "saveBackgroundColor: Background color saved");
    }
    
    // 恢复背景色设置
    private void restoreBackgroundColor() {
        Log.d(TAG, "restoreBackgroundColor: Restoring background color");
        currentBackgroundColor = sharedPreferences.getInt(BACKGROUND_COLOR_PREF, 0xFFADD8E6); // 默认为浅蓝色
        currentTextColor = sharedPreferences.getInt(TEXT_COLOR_PREF, 0xFF000000); // 默认为黑色
        updateBackgroundColor();
        updateTextColor();
        Log.d(TAG, "restoreBackgroundColor: Background color restored to " + String.format("#%06X", (0xFFFFFF & currentBackgroundColor)));
    }
}