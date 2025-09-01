package com.example.myapplication2;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.Spine;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.domain.TOCReference;
import nl.siegmann.epublib.domain.TableOfContents;
import nl.siegmann.epublib.epub.EpubReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import android.os.AsyncTask;

public class BookReaderActivity extends AppCompatActivity {
    private static final String TAG = "BookReaderActivity";

    private TextView titleTextView;
    private TextView contentTextView;
    private Book epubBook;
    private String bookPath; // 保存书籍路径
    private int currentChapterIndex = 0;
    private float startY;
    private float startX;
    private long downTime; // 添加按下时间变量

    // 添加一个变量来引用ScrollView
    private View scrollView;
    private View clickDetectionLayer;

    // 添加翻页按钮引用
    private Button previousButton;
    private Button nextButton;

    // 添加菜单层相关引用
    private FrameLayout menuLayer;
    private ImageButton backButton;
    private ImageButton tocButton;
    private View menuBackground;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_reader);

        Log.d(TAG, "onCreate: BookReaderActivity started");

        // 确保ActionBar显示，以便菜单可以正常显示
        if (getSupportActionBar() != null) {
            getSupportActionBar().show();
        }

        // 初始化视图引用
        initializeViews();
        
        // 初始化点击事件监听器
        initializeClickListeners();
        
        // 初始化书籍加载
        initializeBookLoading();
    }
    
    // 初始化视图引用
    private void initializeViews() {
        titleTextView = findViewById(R.id.titleTextView);
        contentTextView = findViewById(R.id.contentTextView);
        scrollView = findViewById(R.id.scrollView); // 添加对ScrollView的引用
        clickDetectionLayer = findViewById(R.id.clickDetectionLayer);

        // 初始化翻页按钮
        previousButton = findViewById(R.id.previousButton);
        nextButton = findViewById(R.id.nextButton);

        // 初始化菜单层组件
        menuLayer = findViewById(R.id.menuLayer);
        backButton = findViewById(R.id.backButton);
        tocButton = findViewById(R.id.tocButton);
        menuBackground = findViewById(R.id.menuBackground);
    }
    
    // 初始化点击事件监听器
    private void initializeClickListeners() {
        // 默认显示菜单层
        showMenuLayer();

        // 使用post方法将耗时的初始化操作推迟到下一帧执行
        // 这样可以避免阻塞主线程，提高界面启动速度
        clickDetectionLayer.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "开始初始化16个点击区域监听器");
                
                // 获取GridLayout布局，用于管理16个点击区域
                GridLayout gridLayout = findViewById(R.id.gridLayout);
                if (gridLayout == null) {
                    Log.e(TAG, "GridLayout未找到，初始化点击区域失败");
                    return;
                }
                
                // 获取子视图数量并记录日志
                int childCount = gridLayout.getChildCount();
                Log.d(TAG, "找到" + childCount + "个子视图用于点击区域");
                
                // 遍历所有子视图，为每个单元格添加点击监听器
                for (int i = 0; i < childCount; i++) {
                    // 获取当前单元格视图
                    View cell = gridLayout.getChildAt(i);
                    if (cell == null) {
                        Log.w(TAG, "点击区域" + i + "为空，跳过初始化");
                        continue;
                    }
                    
                    // 保存当前索引，供点击事件使用
                    final int index = i;
                    
                    // 获取单元格标签（用于判断是否为中心区域）
                    String tag = (String) cell.getTag();
                    Log.d(TAG, "点击区域" + i + "的标签: " + tag);
                    
                    // 为单元格设置点击监听器
                    cell.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // 判断是否为中心区域
                            boolean isCenter = "center".equals(tag);
                            Log.d(TAG, "点击事件: 区域=" + index + ", 是否为中心区域=" + isCenter);
                            // 调用实际的点击处理方法
                            handleCellClick(index, isCenter);
                        }
                    });
                }
                Log.d(TAG, "点击区域监听器初始化完成");
            }
        });

        // 为区域检测层添加点击监听器
        clickDetectionLayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 这里处理点击事件
            }
        });

        // 设置按钮点击事件
        previousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: 上一页按钮被点击");
                previousPage();
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: 下一页按钮被点击");
                nextPage();
            }
        });

        // 设置菜单层按钮点击事件
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: 返回按钮被点击");
                finish(); // 返回书籍列表
            }
        });

        tocButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: 目录按钮被点击");
                showTableOfContents();
            }
        });

        // 设置菜单背景点击事件，用于隐藏菜单
        menuBackground.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: 菜单背景被点击，隐藏菜单层");
                hideMenuLayer();
            }
        });

        // 设置菜单中间区域点击事件，用于隐藏菜单
        View menuMiddleArea = findViewById(R.id.menuMiddleArea);
        menuMiddleArea.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: 菜单中间区域被点击，隐藏菜单层");
                hideMenuLayer();
            }
        });

        // 设置触摸监听器实现上下滑动翻页和点击显示菜单
        contentTextView.setOnTouchListener(new View.OnTouchListener() {
            private static final int MIN_SCROLL_DISTANCE = 100;
            private static final int MAX_CLICK_DISTANCE = 10;
            private static final int MAX_CLICK_DURATION = 300;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getY();
                        startX = event.getX();
                        downTime = System.currentTimeMillis();
                        Log.d(TAG, "onTouch: ACTION_DOWN at Y=" + startY + ", X=" + startX);
                        // 请求父视图不要拦截触摸事件
                        scrollView.getParent().requestDisallowInterceptTouchEvent(true);
                        return false;

                    case MotionEvent.ACTION_MOVE:
                        Log.d(TAG, "onTouch: ACTION_MOVE at Y=" + event.getY() + ", X=" + event.getX());
                        return false;

                    case MotionEvent.ACTION_UP:
                        long upTime = System.currentTimeMillis();
                        float endY = event.getY();
                        float endX = event.getX();
                        float deltaY = endY - startY;
                        float deltaX = endX - startX;
                        long touchDuration = upTime - downTime;

                        Log.d(TAG, "onTouch: ACTION_UP at Y=" + endY + ", X=" + endX +
                                ", duration=" + touchDuration + "ms, deltaY=" + deltaY + ", deltaX=" + deltaX);

                        boolean isClick = touchDuration < MAX_CLICK_DURATION &&
                                Math.abs(deltaX) < MAX_CLICK_DISTANCE &&
                                Math.abs(deltaY) < MAX_CLICK_DISTANCE;

                        if (isClick) {
                            Log.d(TAG, "onTouch: 点击事件检测到");
                            
                            // 如果菜单层可见，则隐藏菜单层
                            if (menuLayer.getVisibility() == View.VISIBLE) {
                                Log.d(TAG, "onTouch: 菜单层可见，隐藏菜单层");
                                hideMenuLayer();
                            } 
                            // 否则显示菜单层
                            else {
                                Log.d(TAG, "onTouch: 菜单层不可见，显示菜单层");
                                showMenuLayer();
                            }
                            
                            // 释放父视图对触摸事件的控制
                            scrollView.getParent().requestDisallowInterceptTouchEvent(false);
                            return true;
                        }

                        if (Math.abs(deltaY) > MIN_SCROLL_DISTANCE && Math.abs(deltaY) > Math.abs(deltaX)) {
                            if (deltaY > 0) {
                                Log.d(TAG, "onTouch: 向下滑动，跳转到上一页");
                                previousPage();
                            } else {
                                Log.d(TAG, "onTouch: 向上滑动，跳转到下一页");
                                nextPage();
                            }
                            // 释放父视图对触摸事件的控制
                            scrollView.getParent().requestDisallowInterceptTouchEvent(false);
                            return true;
                        }

                        Log.d(TAG, "onTouch: 触摸事件未识别为点击或滑动");
                        // 释放父视图对触摸事件的控制
                        scrollView.getParent().requestDisallowInterceptTouchEvent(false);
                        break;

                    case MotionEvent.ACTION_CANCEL:
                        Log.d(TAG, "onTouch: ACTION_CANCEL");
                        // 释放父视图对触摸事件的控制
                        scrollView.getParent().requestDisallowInterceptTouchEvent(false);
                        break;

                    default:
                        Log.d(TAG, "onTouch: 未知动作 " + event.getAction());
                        break;
                }
                return false;
            }
        });

    }
    
    // 初始化书籍加载
    private void initializeBookLoading() {
        // 从Intent获取书籍路径
        Intent intent = getIntent();
        String bookPath = intent.getStringExtra("book_path");
        Log.d(TAG, "onCreate: 接收到书籍路径: " + bookPath);

        if (bookPath != null) {
            // 显示加载提示
            contentTextView.setText("正在加载书籍...");
            // 在后台线程加载书籍
            new LoadBookTask().execute(bookPath);
        }
    }

    // 添加选项菜单
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu: Creating options menu");
        getMenuInflater().inflate(R.menu.reader_menu, menu);
        return true;
    }

    // 处理菜单项点击事件
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_toc) {
            Log.d(TAG, "onOptionsItemSelected: TOC menu item selected");
            showTableOfContents();
            return true;
        }
        Log.d(TAG, "onOptionsItemSelected: Unknown menu item selected: " + itemId);
        return super.onOptionsItemSelected(item);
    }

    // 后台加载EPUB文件的任务
    private class LoadBookTask extends AsyncTask<String, Void, Book> {
        @Override
        protected Book doInBackground(String... params) {
            String path = params[0];
            Log.d(TAG, "LoadBookTask: Loading book from " + path);
            try {
                File file = new File(path);
                InputStream inputStream = new FileInputStream(file);
                // 使用try-with-resources确保资源正确关闭
                Book book = (new EpubReader()).readEpub(inputStream);
                inputStream.close();
                return book;
            } catch (IOException e) {
                Log.e(TAG, "LoadBookTask: Error loading book: " + e.getMessage());
                return null;
            } catch (Exception e) {
                Log.e(TAG, "LoadBookTask: Unexpected error loading book: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(Book book) {
            if (book != null) {
                epubBook = book;
                Log.d(TAG, "LoadBookTask: Book loaded successfully. Title: " + epubBook.getTitle());
                // 显示第一章
                currentChapterIndex = 0;
                displayCurrentPage();
            } else {
                Log.e(TAG, "LoadBookTask: Failed to load book");
                contentTextView.setText("无法加载书籍");
            }
        }
    }

    private String getChapterContent(int chapterIndex) {
        Log.d(TAG, "getChapterContent: Getting content for chapter " + chapterIndex);
        if (epubBook == null) {
            Log.w(TAG, "getChapterContent: epubBook is null, returning empty string");
            return "";
        }

        Spine spine = epubBook.getSpine();
        if (spine == null || chapterIndex >= spine.size()) {
            Log.w(TAG, "getChapterContent: spine is null or invalid index, returning empty string");
            return "";
        }

        try {
            SpineReference spineReference = spine.getSpineReferences().get(chapterIndex);
            Resource resource = spineReference.getResource();
            
            // 检查资源是否有效
            if (resource == null) {
                Log.w(TAG, "getChapterContent: resource is null");
                return "章节内容无法加载";
            }
            
            // 获取资源数据
            byte[] data = resource.getData();
            if (data == null) {
                Log.w(TAG, "getChapterContent: resource data is null");
                return "章节内容无法加载";
            }
            
            String content = new String(data, "UTF-8");
            
            // 简单提取文本内容（实际应用中可能需要解析HTML）
            content = content.replaceAll("<[^>]*>", "");
            Log.d(TAG, "getChapterContent: Chapter content length: " + content.length());
            return content;
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "getChapterContent: OutOfMemoryError - " + e.getMessage());
            return "章节内容过大，无法加载";
        } catch (Exception e) {
            Log.e(TAG, "getChapterContent: Error getting chapter content: " + e.getMessage());
            return "章节内容加载失败";
        }
    }

    private void displayCurrentPage() {
        Log.d(TAG, "displayCurrentPage: Displaying page " + currentChapterIndex);
        if (epubBook == null) {
            Log.w(TAG, "displayCurrentPage: epubBook is null, returning");
            return;
        }

        Spine spine = epubBook.getSpine();
        if (spine == null || currentChapterIndex >= spine.size()) {
            Log.w(TAG, "displayCurrentPage: spine is null or invalid index");
            return;
        }

        // 按需加载当前章节内容
        String content = getChapterContent(currentChapterIndex);
        contentTextView.setText(content);
        Log.d(TAG, "displayCurrentPage: Content set to text view, length: " + content.length());

        // 显示书名和当前页码
        if (epubBook != null) {
            String title = epubBook.getTitle();
            if (title != null && !title.isEmpty()) {
                titleTextView.setText(title + " (" + (currentChapterIndex + 1) + "/" + spine.size() + ")");
                Log.d(TAG, "displayCurrentPage: Title set to: " + title);
            } else {
                titleTextView.setText("书籍内容 (" + (currentChapterIndex + 1) + "/" + spine.size() + ")");
                Log.d(TAG, "displayCurrentPage: Using default title");
            }
        } else {
            titleTextView.setText("书籍内容 (" + (currentChapterIndex + 1) + "/" + spine.size() + ")");
            Log.d(TAG, "displayCurrentPage: epubBook is null, using default title");
        }

        // 每次显示新页面时滚动到顶部
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.scrollTo(0, 0);
                Log.d(TAG, "displayCurrentPage: Scrolled to top");
            }
        });

        // 更新按钮状态
        updateButtonState();
    }

    // 更新按钮的可用状态
    private void updateButtonState() {
        if (epubBook == null) return;
        
        Spine spine = epubBook.getSpine();
        if (spine == null) return;
        
        boolean prevEnabled = currentChapterIndex > 0;
        boolean nextEnabled = currentChapterIndex < spine.size() - 1;

        previousButton.setEnabled(prevEnabled);
        nextButton.setEnabled(nextEnabled);

        Log.d(TAG, "updateButtonState: Previous button enabled: " + prevEnabled +
                ", Next button enabled: " + nextEnabled +
                " (current index: " + currentChapterIndex +
                ", total chapters: " + spine.size() + ")");
    }

    private void nextPage() {
        Log.d(TAG, "nextPage: 当前索引: " + currentChapterIndex);
        if (epubBook == null) return;
        
        Spine spine = epubBook.getSpine();
        if (spine == null) return;
        
        if (currentChapterIndex < spine.size() - 1) {
            currentChapterIndex++;
            Log.d(TAG, "nextPage: 跳转到下一页，新索引: " + currentChapterIndex);
            displayCurrentPage();
        } else {
            Log.d(TAG, "nextPage: 已经是最后一页了");
            contentTextView.setText("已经是最后一页了");
        }
    }

    private void previousPage() {
        Log.d(TAG, "previousPage: 当前索引: " + currentChapterIndex);
        if (currentChapterIndex > 0) {
            currentChapterIndex--;
            Log.d(TAG, "previousPage: 跳转到上一页，新索引: " + currentChapterIndex);
            displayCurrentPage();
        } else {
            Log.d(TAG, "previousPage: 已经是第一页了");
            contentTextView.setText("已经是第一页了");
        }
    }

    // 显示目录
    private void showTableOfContents() {
        Log.d(TAG, "showTableOfContents: Showing table of contents");
        if (epubBook == null) {
            Log.w(TAG, "showTableOfContents: epubBook is null");
            return;
        }

        TableOfContents toc = epubBook.getTableOfContents();
        if (toc == null || toc.getTocReferences().isEmpty()) {
            Log.d(TAG, "showTableOfContents: No TOC found, showing chapter list");
            // 如果没有目录，则使用章节列表
            showChapterList();
            return;
        }

        List<String> tocItems = new ArrayList<>();
        List<TOCReference> tocReferences = toc.getTocReferences();
        Log.d(TAG, "showTableOfContents: Found " + tocReferences.size() + " TOC items");
        for (int i = 0; i < tocReferences.size(); i++) {
            TOCReference ref = tocReferences.get(i);
            String title = ref.getTitle();
            if (title != null && !title.isEmpty()) {
                tocItems.add(title);
            } else {
                tocItems.add("章节 " + (i + 1));
            }
        }

        showTocDialog(tocItems, tocReferences);
    }

    // 显示章节列表（当没有目录时使用）
    private void showChapterList() {
        Log.d(TAG, "showChapterList: Showing chapter list");
        if (epubBook == null) {
            Log.w(TAG, "showChapterList: epubBook is null");
            return;
        }
        
        Spine spine = epubBook.getSpine();
        if (spine == null) {
            Log.w(TAG, "showChapterList: spine is null");
            return;
        }

        List<String> chapterTitles = new ArrayList<>();
        for (int i = 0; i < spine.size(); i++) {
            chapterTitles.add("章节 " + (i + 1));
        }

        // 创建章节引用列表（模拟）
        List<TOCReference> fakeReferences = new ArrayList<>();
        for (int i = 0; i < spine.size(); i++) {
            fakeReferences.add(null); // 我们只使用索引
        }

        showTocDialog(chapterTitles, fakeReferences);
    }

    // 显示目录对话框
    private void showTocDialog(List<String> items, List<TOCReference> references) {
        Log.d(TAG, "showTocDialog: Showing TOC dialog with " + items.size() + " items");
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("书籍目录");

        String[] itemsArray = items.toArray(new String[0]);
        builder.setItems(itemsArray, (dialog, which) -> {
            Log.d(TAG, "showTocDialog: Item selected: " + which);
            // 跳转到选中的章节
            if (which >= 0) {
                currentChapterIndex = which;
                Log.d(TAG, "showTocDialog: Jumping to chapter " + which);
                displayCurrentPage();
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> {
            Log.d(TAG, "showTocDialog: Cancel button clicked");
        });
        builder.show();
    }

    // 显示菜单层
    private void showMenuLayer() {
        Log.d(TAG, "显示菜单层");
        menuLayer.setVisibility(View.VISIBLE);
        // 隐藏区域检测层，避免干扰
        clickDetectionLayer.setVisibility(View.GONE);
    }

    // 隐藏菜单层
    private void hideMenuLayer() {
        Log.d(TAG, "隐藏菜单层");
        menuLayer.setVisibility(View.GONE);
        // 显示区域检测层
        clickDetectionLayer.setVisibility(View.VISIBLE);
    }

    // 处理单元格点击事件
    private void handleCellClick(int index, boolean isCenter) {
        Log.d(TAG, "handleCellClick: 点击了区域 " + index + (isCenter ? " (中间区域)" : " (边缘区域)"));
        
        // 只在中间区域点击时显示菜单层
        if (isCenter) {
            Log.d(TAG, "handleCellClick: 在中间区域点击，显示菜单层");
            if (menuLayer.getVisibility() != View.VISIBLE) {
                showMenuLayer();
            } else {
                hideMenuLayer();
            }
        } else {
            Log.d(TAG, "handleCellClick: 在边缘区域点击，不执行操作");
        }
    }

    // 显示/隐藏菜单层
    private void toggleMenuLayer() {
        if (menuLayer.getVisibility() == View.VISIBLE) {
            hideMenuLayer();
        } else {
            showMenuLayer();
        }
    }
}