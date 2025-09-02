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
    
    // 分页相关变量
    private List<String> pageContents = new ArrayList<>(); // 存储分页后的内容
    private int currentPageIndex = 0; // 当前页索引

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
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: 调整内容区域以适应屏幕尺寸");
        // 屏幕尺寸可能已更改（如旋转），重新调整内容区域
        adjustContentArea();
        
        // 调整文本视图的行间距和字距
        adjustTextSpacing();
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
        
        // 动态调整内容区域
        adjustContentArea();
        
        // 动态设置文本视图的行间距和字距
        adjustTextSpacing();
    }
    
    /**
     * 动态调整文本视图的行间距和字距
     */
    private void adjustTextSpacing() {
        if (contentTextView != null) {
            // 根据屏幕密度动态设置行间距
            float density = getResources().getDisplayMetrics().density;
            float lineSpacingExtra = 4 * density; // 基础行间距4dp
            float lineSpacingMultiplier = 1.2f;   // 行间距倍数
            
            contentTextView.setLineSpacing(lineSpacingExtra, lineSpacingMultiplier);
            
            // 可以根据需要调整字距（需要API 21及以上）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                contentTextView.setLetterSpacing(0.02f); // 字距调整
            }
            
            Log.d(TAG, "adjustTextSpacing: 设置行间距和字距完成");
        }
    }
    
    /**
     * 动态调整内容显示区域，确保在不同屏幕尺寸上正确显示
     */
    private void adjustContentArea() {
        // 使用post方法确保在布局完成后执行
        contentTextView.post(new Runnable() {
            @Override
            public void run() {
                // 获取屏幕高度
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                
                // 获取按钮区域高度
                View buttonLayout = findViewById(R.id.buttonLayout);
                int buttonHeight = buttonLayout != null ? buttonLayout.getHeight() : 0;
                
                // 获取标题区域高度
                int titleHeight = titleTextView != null ? titleTextView.getHeight() : 0;
                
                // 计算内容区域可用高度(使用固定值32dp替代不存在的R.dimen.activity_vertical_margin)
                int padding = (int) (32 * getResources().getDisplayMetrics().density);
                int availableHeight = screenHeight - buttonHeight - titleHeight - padding;
                
                Log.d(TAG, "屏幕高度: " + screenHeight + ", 按钮高度: " + buttonHeight + 
                        ", 标题高度: " + titleHeight + ", 可用高度: " + availableHeight);
            }
        });
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
            try (FileInputStream inputStream = new FileInputStream(new File(path))) {
                // 使用try-with-resources确保资源正确关闭
                Book book = (new EpubReader()).readEpub(inputStream);
                return book;
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "LoadBookTask: OutOfMemoryError loading book: " + e.getMessage());
                return null;
            } catch (IOException e) {
                Log.e(TAG, "LoadBookTask: IOException loading book: " + e.getMessage());
                return null;
            } catch (Exception e) {
                Log.e(TAG, "LoadBookTask: Unexpected error loading book: " + e.getMessage());
                return null;
            } catch (Throwable t) {
                Log.e(TAG, "LoadBookTask: Unexpected throwable loading book: " + t.getMessage());
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
            if (spineReference == null) {
                Log.w(TAG, "getChapterContent: spineReference is null");
                return "章节内容无法加载";
            }
            
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
            
            // 检查数据长度
            if (data.length == 0) {
                Log.w(TAG, "getChapterContent: resource data is empty");
                return "章节内容为空";
            }
            
            String content = new String(data, "UTF-8");
            
            // 检查内容是否为空
            if (content == null || content.isEmpty()) {
                Log.w(TAG, "getChapterContent: content is null or empty");
                return "章节内容为空";
            }
            
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
        } catch (Throwable t) {
            Log.e(TAG, "getChapterContent: Unexpected error getting chapter content: " + t.getMessage());
            return "章节内容加载失败";
        }
    }
    
    /**
     * 将章节内容分页
     * @param content 章节完整内容
     * @return 分页后的内容列表
     */
    private List<String> paginateChapterContent(String content) {
        List<String> pages = new ArrayList<>();
        try {
            if (content == null || content.isEmpty()) {
                pages.add("");
                return pages;
            }
            
            // 根据屏幕尺寸动态计算每页字符数
            int charsPerPage = calculateCharsPerPage();
            
            // 分割内容为多页，确保句子完整性
            int length = content.length();
            int start = 0;
            
            while (start < length) {
                int end = Math.min(start + charsPerPage, length);
                
                // 尽量在句号或段落结尾处分页
                if (end < length) {
                    // 向后查找句号或段落结尾
                    while (end > start && content.charAt(end) != '。' && content.charAt(end) != '\n') {
                        end--;
                    }
                    
                    // 如果找不到句号或段落结尾，则在原位置分页
                    if (end <= start) {
                        end = start + charsPerPage;
                    } else {
                        // 包含句号或换行符
                        end++;
                    }
                }
                
                if (end > start) {
                    pages.add(content.substring(start, end));
                }
                
                start = end;
            }
            
            // 如果没有分页，则添加整个内容
            if (pages.isEmpty()) {
                pages.add(content);
            }
        } catch (Exception e) {
            Log.e(TAG, "paginateChapterContent: Error paginating content: " + e.getMessage());
            pages.clear();
            pages.add("内容分页失败");
        } catch (Throwable t) {
            Log.e(TAG, "paginateChapterContent: Unexpected error paginating content: " + t.getMessage());
            pages.clear();
            pages.add("内容分页失败");
        }
        
        return pages;
    }
    
    /**
     * 根据屏幕尺寸计算每页可显示的字符数
     * @return 每页字符数
     */
    private int calculateCharsPerPage() {
        // 获取屏幕相关信息
        android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenHeight = displayMetrics.heightPixels;
        int screenWidth = displayMetrics.widthPixels;
        float density = displayMetrics.density;
        
        // 获取按钮区域和标题区域的大致高度
        int buttonAreaHeight = (int) (80 * density); // 按钮区域高度约80dp
        int titleAreaHeight = (int) (80 * density);   // 标题区域高度约80dp
        int padding = (int) (32 * density);           // 上下padding共32dp
        
        // 计算可用于内容显示的高度
        int contentHeight = screenHeight - buttonAreaHeight - titleAreaHeight - padding;
        
        // 获取文本视图的文本大小
        float textSize = 16f; // 默认字体大小16sp
        if (contentTextView != null) {
            textSize = contentTextView.getTextSize() / density; // 转换为dp
        }
        
        // 估算每行字符数（根据屏幕宽度和字体大小）
        int charsPerLine = (int) (screenWidth / (textSize * density));
        
        // 估算每页行数（根据内容高度和行高）
        int lineHeight = (int) ((textSize + 8) * density); // 文本大小+行间距
        int linesPerPage = contentHeight / lineHeight;
        
        // 计算每页字符数
        int charsPerPage = charsPerLine * linesPerPage;
        
        Log.d(TAG, "屏幕尺寸: " + screenWidth + "x" + screenHeight + 
                ", 密度: " + density + 
                ", 每页字符数估算: " + charsPerPage +
                ", 每页行数: " + linesPerPage +
                ", 每行字符数: " + charsPerLine);
        
        // 确保至少有最小字符数，避免页面过少
        return Math.max(charsPerPage, 500);
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

        try {
            // 按需加载当前章节内容
            String content = getChapterContent(currentChapterIndex);
            
            // 对内容进行分页处理
            pageContents = paginateChapterContent(content);
            
            // 重置当前页索引
            currentPageIndex = 0;
            
            // 显示当前页内容
            if (contentTextView != null) {
                if (pageContents != null && !pageContents.isEmpty()) {
                    contentTextView.setText(pageContents.get(currentPageIndex));
                } else {
                    contentTextView.setText("");
                }
                
                // 调整文本视图的行间距和字距
                adjustTextSpacing();
            }
            
            Log.d(TAG, "displayCurrentPage: Content set to text view, length: " + 
                    (pageContents != null && !pageContents.isEmpty() ? pageContents.get(currentPageIndex).length() : 0));

            // 显示书名和当前页码
            if (titleTextView != null) {
                if (epubBook != null) {
                    String title = epubBook.getTitle();
                    if (title != null && !title.isEmpty()) {
                        titleTextView.setText(title + " (" + (currentChapterIndex + 1) + "/" + spine.size() + ")" + 
                                " 第" + (currentPageIndex + 1) + "页/" + (pageContents != null ? pageContents.size() : 0) + "页");
                        Log.d(TAG, "displayCurrentPage: Title set to: " + title);
                    } else {
                        titleTextView.setText("书籍内容 (" + (currentChapterIndex + 1) + "/" + spine.size() + ")" + 
                                " 第" + (currentPageIndex + 1) + "页/" + (pageContents != null ? pageContents.size() : 0) + "页");
                        Log.d(TAG, "displayCurrentPage: Using default title");
                    }
                } else {
                    titleTextView.setText("书籍内容 (" + (currentChapterIndex + 1) + "/" + spine.size() + ")" + 
                            " 第" + (currentPageIndex + 1) + "页/" + (pageContents != null ? pageContents.size() : 0) + "页");
                    Log.d(TAG, "displayCurrentPage: epubBook is null, using default title");
                }
            }

            // 每次显示新页面时滚动到顶部
            if (scrollView != null) {
                scrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollView.scrollTo(0, 0);
                        Log.d(TAG, "displayCurrentPage: Scrolled to top");
                    }
                });
            }

            // 更新按钮状态
            updateButtonState();
        } catch (Exception e) {
            Log.e(TAG, "displayCurrentPage: Error displaying page: " + e.getMessage());
            if (contentTextView != null) {
                contentTextView.setText("页面显示失败: " + e.getMessage());
            }
        } catch (Throwable t) {
            Log.e(TAG, "displayCurrentPage: Unexpected error displaying page: " + t.getMessage());
            if (contentTextView != null) {
                contentTextView.setText("页面显示失败");
            }
        }
    }

    // 更新按钮的可用状态
    private void updateButtonState() {
        if (epubBook == null) return;
        
        Spine spine = epubBook.getSpine();
        if (spine == null) return;
        
        // 计算上一页和下一页按钮的可用状态
        boolean prevEnabled = (currentChapterIndex > 0) || (currentPageIndex > 0);
        boolean nextEnabled = (currentChapterIndex < spine.size() - 1) || (currentPageIndex < pageContents.size() - 1);

        previousButton.setEnabled(prevEnabled);
        nextButton.setEnabled(nextEnabled);

        Log.d(TAG, "updateButtonState: Previous button enabled: " + prevEnabled +
                ", Next button enabled: " + nextEnabled +
                " (chapter index: " + currentChapterIndex +
                ", page index: " + currentPageIndex +
                ", total chapters: " + spine.size() + 
                ", total pages in current chapter: " + pageContents.size() + ")");
    }

    private void nextPage() {
        Log.d(TAG, "nextPage: 当前章节索引: " + currentChapterIndex + ", 当前页索引: " + currentPageIndex);
        if (epubBook == null) return;
        
        Spine spine = epubBook.getSpine();
        if (spine == null) return;
        
        // 如果当前页不是章节的最后一页，则显示下一页
        if (currentPageIndex < pageContents.size() - 1) {
            currentPageIndex++;
            if (contentTextView != null) {
                contentTextView.setText(pageContents.get(currentPageIndex));
                // 调整文本视图的行间距和字距
                adjustTextSpacing();
            }
            
            // 更新标题显示
            if (epubBook != null) {
                String title = epubBook.getTitle();
                if (title != null && !title.isEmpty()) {
                    titleTextView.setText(title + " (" + (currentChapterIndex + 1) + "/" + spine.size() + ")" + 
                            " 第" + (currentPageIndex + 1) + "页/" + pageContents.size() + "页");
                } else {
                    titleTextView.setText("书籍内容 (" + (currentChapterIndex + 1) + "/" + spine.size() + ")" + 
                            " 第" + (currentPageIndex + 1) + "页/" + pageContents.size() + "页");
                }
            }
            
            Log.d(TAG, "nextPage: 显示章节内下一页，页索引: " + currentPageIndex);
        } 
        // 如果当前页是章节的最后一页，且不是最后一章，则跳转到下一章
        else if (currentChapterIndex < spine.size() - 1) {
            currentChapterIndex++;
            Log.d(TAG, "nextPage: 跳转到下一章，新章节索引: " + currentChapterIndex);
            displayCurrentPage();
        } else {
            Log.d(TAG, "nextPage: 已经是最后一页了");
            if (contentTextView != null) {
                contentTextView.setText("已经是最后一页了");
            }
        }
        
        // 更新按钮状态
        updateButtonState();
    }

    private void previousPage() {
        Log.d(TAG, "previousPage: 当前章节索引: " + currentChapterIndex + ", 当前页索引: " + currentPageIndex);
        
        // 如果当前页不是章节的第一页，则显示上一页
        if (currentPageIndex > 0) {
            currentPageIndex--;
            if (contentTextView != null) {
                contentTextView.setText(pageContents.get(currentPageIndex));
                // 调整文本视图的行间距和字距
                adjustTextSpacing();
            }
            
            // 更新标题显示
            if (epubBook != null) {
                Spine spine = epubBook.getSpine();
                String title = epubBook.getTitle();
                if (title != null && !title.isEmpty()) {
                    titleTextView.setText(title + " (" + (currentChapterIndex + 1) + "/" + spine.size() + ")" + 
                            " 第" + (currentPageIndex + 1) + "页/" + pageContents.size() + "页");
                } else {
                    titleTextView.setText("书籍内容 (" + (currentChapterIndex + 1) + "/" + spine.size() + ")" + 
                            " 第" + (currentPageIndex + 1) + "页/" + pageContents.size() + "页");
                }
            }
            
            Log.d(TAG, "previousPage: 显示章节内上一页，页索引: " + currentPageIndex);
        } 
        // 如果当前页是章节的第一页，且不是第一章，则跳转到上一章
        else if (currentChapterIndex > 0) {
            currentChapterIndex--;
            Log.d(TAG, "previousPage: 跳转到上一章，新章节索引: " + currentChapterIndex);
            displayCurrentPage();
        } else {
            Log.d(TAG, "previousPage: 已经是第一页了");
            if (contentTextView != null) {
                contentTextView.setText("已经是第一页了");
            }
        }
        
        // 更新按钮状态
        updateButtonState();
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