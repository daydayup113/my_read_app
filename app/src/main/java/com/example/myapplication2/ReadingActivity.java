package com.example.myapplication2;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.util.List;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Spine;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.domain.TOCReference;
import nl.siegmann.epublib.domain.TableOfContents;
import nl.siegmann.epublib.epub.EpubReader;

public class ReadingActivity extends AppCompatActivity {
    private static final String TAG = "ReadingActivity";
    private static final String PREFS_NAME = "ReadingProgress";
    private ScrollView contentScrollView;
    private TextView contentTextView;
    private View menuLayer;
    private Button previousPageButton;
    private Button nextPageButton;
    private boolean isMenuVisible = false;
    private Book epubBook;
    private String bookTitle;
    private Uri bookUri;
    private int currentPage = 0;
    private List<SpineReference> spineReferences;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading);

        // 获取传递的书籍信息
        String uriString = getIntent().getStringExtra("book_uri");
        bookTitle = getIntent().getStringExtra("book_title");
        if (uriString != null) {
            bookUri = Uri.parse(uriString);
        }

        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initViews();
        setupClickListeners();
        loadBookContent();
        
        // 处理返回键事件
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isMenuVisible) {
                    hideMenu();
                } else {
                    finish();
                }
            }
        });
    }

    private void initViews() {
        contentScrollView = findViewById(R.id.contentScrollView);
        contentTextView = findViewById(R.id.contentTextView);
        menuLayer = findViewById(R.id.menuLayer);
        previousPageButton = findViewById(R.id.previousPageButton);
        nextPageButton = findViewById(R.id.nextPageButton);
        
        // 设置滚动监听，用于检测是否需要加载更多内容
        // 注意：setOnScrollChangeListener需要API 23及以上
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            contentScrollView.setOnScrollChangeListener((View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) -> {
                // 可以在这里实现滚动到底部时加载更多内容的逻辑
            });
        }
    }

    private void setupClickListeners() {
        // 点击内容区域切换菜单显示/隐藏
        contentScrollView.setOnClickListener(v -> toggleMenu());

        // 点击菜单层中间区域隐藏菜单
        View menuCenterArea = findViewById(R.id.menuCenterArea);
        menuCenterArea.setOnClickListener(v -> hideMenu());

        // 返回按钮
        findViewById(R.id.backButton).setOnClickListener(v -> onBackPressed());

        // 目录按钮
        findViewById(R.id.tocButton).setOnClickListener(v -> openTableOfContents());
        
        // 翻页按钮
        previousPageButton.setOnClickListener(v -> previousPage());
        nextPageButton.setOnClickListener(v -> nextPage());
    }

    private void loadBookContent() {
        new Thread(() -> {
            try {
                epubBook = loadEpubBook();
                if (epubBook != null) {
                    // 获取书籍的spine references
                    Spine spine = epubBook.getSpine();
                    spineReferences = spine.getSpineReferences();
                    
                    // 恢复阅读进度
                    int savedPage = getSavedProgress();
                    // 加载保存的页面内容或第一页内容
                    loadPageContent(savedPage, true); // 恢复进度时保持滚动位置
                } else {
                    runOnUiThread(() -> {
                        contentTextView.setText("无法加载书籍内容");
                        Toast.makeText(ReadingActivity.this, "加载书籍失败", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "加载EPUB文件时出错", e);
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

    private Book loadEpubBook() {
        try (InputStream epubInputStream = getContentResolver().openInputStream(bookUri)) {
            return new EpubReader().readEpub(epubInputStream);
        } catch (Exception e) {
            Log.e(TAG, "解析EPUB文件时出错", e);
            return null;
        }
    }
    
    private void loadPageContent(int pageIndex, boolean preserveScrollPosition) {
        if (spineReferences == null || pageIndex < 0 || pageIndex >= spineReferences.size()) {
            runOnUiThread(() -> {
                contentTextView.setText("没有更多内容");
            });
            return;
        }
        
        new Thread(() -> {
            try {
                SpineReference spineReference = spineReferences.get(pageIndex);
                String content = new String(spineReference.getResource().getData());
                
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
                        contentScrollView.scrollTo(0, 0);
                    }
                    
                    Toast.makeText(ReadingActivity.this, "第 " + (currentPage + 1) + " 章", Toast.LENGTH_SHORT).show();
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
        
        // 更新翻页按钮的可见性
        previousPageButton.setVisibility(View.VISIBLE);
        nextPageButton.setVisibility(View.VISIBLE);
        
        // 更新按钮的可用性
        previousPageButton.setEnabled(currentPage > 0);
        nextPageButton.setEnabled(currentPage < spineReferences.size() - 1);
    }

    private void toggleMenu() {
        if (isMenuVisible) {
            hideMenu();
        } else {
            showMenu();
        }
    }

    private void showMenu() {
        menuLayer.setVisibility(View.VISIBLE);
        isMenuVisible = true;
        // 隐藏翻页按钮
        previousPageButton.setVisibility(View.GONE);
        nextPageButton.setVisibility(View.GONE);
    }

    private void hideMenu() {
        menuLayer.setVisibility(View.GONE);
        isMenuVisible = false;
        // 显示翻页按钮
        updatePageButtons();
    }

    private void openTableOfContents() {
        if (epubBook != null) {
            TableOfContents toc = epubBook.getTableOfContents();
            List<TOCReference> tocReferences = toc.getTocReferences();
            
            if (tocReferences != null && !tocReferences.isEmpty()) {
                // 启动目录界面
                Toast.makeText(this, "打开目录", Toast.LENGTH_SHORT).show();
                hideMenu();
            } else {
                Toast.makeText(this, "该书籍没有目录信息", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "书籍未加载完成", Toast.LENGTH_SHORT).show();
        }
    }
    
    // 添加翻页方法
    private void nextPage() {
        if (spineReferences != null && currentPage < spineReferences.size() - 1) {
            loadPageContent(currentPage + 1);
            // 保存阅读进度
            saveProgress(currentPage + 1);
        } else {
            Toast.makeText(this, "已经是最后一页", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void previousPage() {
        if (currentPage > 0) {
            loadPageContent(currentPage - 1);
            // 保存阅读进度
            saveProgress(currentPage - 1);
        } else {
            Toast.makeText(this, "已经是第一页", Toast.LENGTH_SHORT).show();
        }
    }
    
    // 保存阅读进度
    private void saveProgress(int page) {
        if (bookUri != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt(bookUri.toString(), page);
            editor.apply();
        }
    }
    
    // 获取保存的阅读进度
    private int getSavedProgress() {
        if (bookUri != null) {
            return sharedPreferences.getInt(bookUri.toString(), 0);
        }
        return 0;
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // 在页面暂停时保存当前进度
        saveProgress(currentPage);
    }
}