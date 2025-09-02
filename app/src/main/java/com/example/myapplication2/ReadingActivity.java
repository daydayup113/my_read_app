package com.example.myapplication2;

import android.content.Intent;
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
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
    private static final int TABLE_OF_CONTENTS_REQUEST = 1;
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
    
    // 添加ActivityResultLauncher来处理目录页面的返回结果
    private ActivityResultLauncher<Intent> tocActivityResultLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading);
        Log.d(TAG, "onCreate: ReadingActivity started");

        // 初始化ActivityResultLauncher
        initActivityResultLauncher();

        // 获取传递的书籍信息
        String uriString = getIntent().getStringExtra("book_uri");
        bookTitle = getIntent().getStringExtra("book_title");
        if (uriString != null) {
            bookUri = Uri.parse(uriString);
        }
        Log.d(TAG, "onCreate: bookUri=" + bookUri + ", bookTitle=" + bookTitle);

        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initViews();
        setupClickListeners();
        loadBookContent();
        
        // 处理返回键事件
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Log.d(TAG, "handleOnBackPressed: isMenuVisible=" + isMenuVisible);
                if (isMenuVisible) {
                    hideMenu();
                } else {
                    finish();
                }
            }
        });
    }

    private void initActivityResultLauncher() {
        tocActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
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
                    }
                });
    }

    private void initViews() {
        Log.d(TAG, "initViews: Initializing views");
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
        Log.d(TAG, "setupClickListeners: Setting up click listeners");
        // 点击内容区域切换菜单显示/隐藏
        contentScrollView.setOnClickListener(v -> {
            Log.d(TAG, "contentScrollView clicked: toggling menu");
            toggleMenu();
        });

        // 点击菜单层中间区域隐藏菜单
        View menuCenterArea = findViewById(R.id.menuCenterArea);
        menuCenterArea.setOnClickListener(v -> {
            Log.d(TAG, "menuCenterArea clicked: hiding menu");
            hideMenu();
        });

        // 返回按钮
        findViewById(R.id.backButton).setOnClickListener(v -> {
            Log.d(TAG, "backButton clicked");
            onBackPressed();
        });

        // 目录按钮
        findViewById(R.id.tocButton).setOnClickListener(v -> {
            Log.d(TAG, "tocButton clicked");
            openTableOfContents();
        });
        
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

    private void loadBookContent() {
        Log.d(TAG, "loadBookContent: Starting to load book content");
        new Thread(() -> {
            try {
                epubBook = loadEpubBook();
                if (epubBook != null) {
                    Log.d(TAG, "loadBookContent: EPUB book loaded successfully");
                    // 获取书籍的spine references
                    Spine spine = epubBook.getSpine();
                    spineReferences = spine.getSpineReferences();
                    Log.d(TAG, "loadBookContent: spineReferences size=" + spineReferences.size());
                    
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
        Log.d(TAG, "loadEpubBook: Loading EPUB book from URI: " + bookUri);
        try (InputStream epubInputStream = getContentResolver().openInputStream(bookUri)) {
            return new EpubReader().readEpub(epubInputStream);
        } catch (Exception e) {
            Log.e(TAG, "解析EPUB文件时出错", e);
            return null;
        }
    }
    
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
        // 隐藏翻页按钮
        previousPageButton.setVisibility(View.GONE);
        nextPageButton.setVisibility(View.GONE);
    }

    private void hideMenu() {
        Log.d(TAG, "hideMenu: Hiding menu");
        menuLayer.setVisibility(View.GONE);
        isMenuVisible = false;
        // 显示翻页按钮
        updatePageButtons();
    }

    private void openTableOfContents() {
        Log.d(TAG, "openTableOfContents: epubBook=" + (epubBook != null));
        if (epubBook != null) {
            TableOfContents toc = epubBook.getTableOfContents();
            List<TOCReference> tocReferences = toc.getTocReferences();
            
            if (tocReferences != null && !tocReferences.isEmpty()) {
                // 启动目录界面并传递书籍信息
                Intent intent = new Intent(this, TableOfContentsActivity.class);
                intent.putExtra("book_uri", bookUri.toString());
                intent.putExtra("book_title", bookTitle);
                tocActivityResultLauncher.launch(intent);
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
        Log.d(TAG, "nextPage: currentPage=" + currentPage);
        if (spineReferences != null && currentPage < spineReferences.size() - 1) {
            loadPageContent(currentPage + 1);
            // 保存阅读进度
            saveProgress(currentPage + 1);
        } else {
            Toast.makeText(this, "已经是最后一页", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void previousPage() {
        Log.d(TAG, "previousPage: currentPage=" + currentPage);
        if (currentPage > 0) {
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
        }
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
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: Saving current progress, currentPage=" + currentPage);
        // 在页面暂停时保存当前进度
        saveProgress(currentPage);
    }
}