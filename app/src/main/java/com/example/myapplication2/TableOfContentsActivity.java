package com.example.myapplication2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import nl.siegmann.epublib.domain.TOCReference;
import nl.siegmann.epublib.domain.TableOfContents;
import nl.siegmann.epublib.epub.EpubReader;
import nl.siegmann.epublib.domain.Book;

public class TableOfContentsActivity extends AppCompatActivity {
    private static final String TAG = "TOCActivity";
    private ListView tocListView;
    private View progressIndicator;
    private FrameLayout progressBarContainer;
    private TOCAdapter tocAdapter;
    private List<TOCReference> chapters;
    private Book epubBook;
    private boolean isTrackingTouch = false; // 标记是否正在拖动进度条
    private int currentChapterPosition = -1; // 当前章节位置
    private boolean isScrolling = false; // 标记列表是否正在滚动
    private Handler hideHandler = new Handler();
    private Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            hideProgressBar();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_toc);

        // 获取传递的当前章节位置
        Intent intent = getIntent();
        currentChapterPosition = intent.getIntExtra("current_chapter", -1);
        Log.d(TAG, "Received currentChapterPosition: " + currentChapterPosition);
        
        initViews();
        loadTableOfContents();
        setupListViewListener();
    }

    private void initViews() {
        tocListView = findViewById(R.id.tocListView);
        progressIndicator = findViewById(R.id.progressIndicator);
        progressBarContainer = findViewById(R.id.progressBarContainer);
        
        chapters = new ArrayList<>();
        tocAdapter = new TOCAdapter(this, chapters, currentChapterPosition); // 传递当前章节位置给适配器
        tocListView.setAdapter(tocAdapter);

        tocListView.setOnItemClickListener((parent, view, position, id) -> {
            // 跳转到指定章节
            TOCReference chapter = chapters.get(position);
            String title = chapter.getTitle();
            
            // 获取章节的资源信息
            Intent resultIntent = new Intent();
            resultIntent.putExtra("chapter_position", position);
            resultIntent.putExtra("chapter_title", title);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
        
        // 设置进度条容器触摸监听
        progressBarContainer.setOnTouchListener((v, event) -> {
            handleProgressTouch(event);
            return true;
        });
        
        // 初始化进度指示器位置
        updateProgressIndicator(0);
    }

    private void handleProgressTouch(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isTrackingTouch = true;
                showProgressBar();
                scrollToPosition(event.getY());
                break;
            case MotionEvent.ACTION_MOVE:
                scrollToPosition(event.getY());
                break;
            case MotionEvent.ACTION_UP:
                isTrackingTouch = false;
                // 如果不在滚动状态，1秒后隐藏进度条
                if (!isScrolling) {
                    hideHandler.postDelayed(hideRunnable, 1000);
                }
                break;
        }
    }

    private void scrollToPosition(float y) {
        if (chapters.size() == 0) return;
        
        // 获取进度条容器的高度和位置
        int[] location = new int[2];
        progressBarContainer.getLocationOnScreen(location);
        int containerTop = location[1];
        int containerHeight = progressBarContainer.getHeight();
        
        // 计算触摸点相对于容器的位置
        float relativeY = y - containerTop;
        
        // 计算进度比例
        float progress = relativeY / containerHeight;
        progress = Math.max(0, Math.min(1, progress)); // 限制在0-1之间
        
        // 计算对应的章节位置
        int chapterIndex = (int) (progress * (chapters.size() - 1));
        chapterIndex = Math.max(0, Math.min(chapterIndex, chapters.size() - 1)); // 确保章节索引在有效范围内
        
        // 滚动到对应章节
        tocListView.setSelection(chapterIndex);
        
        // 更新进度指示器位置
        updateProgressIndicator(progress);
    }

    private void setupListViewListener() {
        tocListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                switch (scrollState) {
                    case AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
                    case AbsListView.OnScrollListener.SCROLL_STATE_FLING:
                        // 用户开始滚动，显示进度条
                        isScrolling = true;
                        showProgressBar();
                        // 取消之前的隐藏任务
                        hideHandler.removeCallbacks(hideRunnable);
                        break;
                    case AbsListView.OnScrollListener.SCROLL_STATE_IDLE:
                        // 滚动停止，1秒后隐藏进度条
                        isScrolling = false;
                        hideHandler.postDelayed(hideRunnable, 1000);
                        break;
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                // 当用户手动滚动列表时，更新进度条位置
                if (!isTrackingTouch && totalItemCount > 0) {
                    // 计算滚动进度比例
                    float progress = 0;
                    if (visibleItemCount < totalItemCount) {
                        progress = (float) firstVisibleItem / (totalItemCount - visibleItemCount);
                    }
                    // 确保进度在有效范围内
                    progress = Math.max(0, Math.min(1, progress));
                    
                    // 更新进度指示器位置
                    updateProgressIndicator(progress);
                }
            }
        });
    }
    
    // 显示进度条
    private void showProgressBar() {
        if (progressBarContainer.getVisibility() != View.VISIBLE) {
            progressBarContainer.setVisibility(View.VISIBLE);
            // 添加淡入动画
            progressBarContainer.setAlpha(0f);
            progressBarContainer.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start();
        }
    }
    
    // 隐藏进度条
    private void hideProgressBar() {
        if (progressBarContainer.getVisibility() == View.VISIBLE && !isTrackingTouch && !isScrolling) {
            // 添加淡出动画
            progressBarContainer.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> progressBarContainer.setVisibility(View.GONE))
                    .start();
        }
    }

    private void updateProgressIndicator(float progress) {
        // 更新进度指示器位置
        progressBarContainer.post(() -> {
            int containerHeight = progressBarContainer.getHeight();
            int indicatorHeight = progressIndicator.getHeight();
            
            // 计算指示器的顶部位置，确保不会超出边界
            float yPosition = progress * (containerHeight - indicatorHeight);
            yPosition = Math.max(0, Math.min(yPosition, containerHeight - indicatorHeight));
            
            // 更新指示器位置
            progressIndicator.setY(yPosition);
        });
    }

    private void loadTableOfContents() {
        // 获取传递的书籍信息
        Intent intent = getIntent();
        String uriString = intent.getStringExtra("book_uri");
        String bookTitle = intent.getStringExtra("book_title");
        
        if (uriString != null) {
            try {
                Uri bookUri = Uri.parse(uriString);
                // 从URI加载EPUB书籍
                try (InputStream epubInputStream = getContentResolver().openInputStream(bookUri)) {
                    epubBook = new EpubReader().readEpub(epubInputStream);
                }
                
                if (epubBook != null) {
                    TableOfContents toc = epubBook.getTableOfContents();
                    List<TOCReference> tocReferences = toc.getTocReferences();
                    
                    if (tocReferences != null && !tocReferences.isEmpty()) {
                        chapters.addAll(tocReferences);
                        tocAdapter.notifyDataSetChanged();
                        
                        // 如果有当前章节位置，则滚动到该位置
                        if (currentChapterPosition >= 0 && currentChapterPosition < chapters.size()) {
                            tocListView.setSelection(currentChapterPosition);
                            
                            // 延迟更新进度条位置，确保列表已经布局完成
                            tocListView.post(() -> {
                                float progress = (float) currentChapterPosition / (chapters.size() - 1);
                                progress = Math.max(0, Math.min(1, progress));
                                updateProgressIndicator(progress);
                            });
                        }
                        
                        Log.d(TAG, "成功加载 " + tocReferences.size() + " 个目录项");
                    } else {
                        Log.w(TAG, "书籍没有目录信息");
                        Toast.makeText(this, "该书籍没有目录信息", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e(TAG, "无法加载EPUB书籍");
                    Toast.makeText(this, "无法加载EPUB书籍", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "加载目录时出错", e);
                Toast.makeText(this, "加载目录时出错: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Log.e(TAG, "未接收到书籍URI");
            Toast.makeText(this, "未接收到书籍信息", Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理Handler回调
        hideHandler.removeCallbacksAndMessages(null);
    }
}