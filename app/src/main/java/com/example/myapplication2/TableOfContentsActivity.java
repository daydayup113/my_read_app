package com.example.myapplication2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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
    private TOCAdapter tocAdapter;
    private List<TOCReference> chapters;
    private Book epubBook;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_toc);

        initViews();
        loadTableOfContents();
    }

    private void initViews() {
        tocListView = findViewById(R.id.tocListView);
        chapters = new ArrayList<>();
        tocAdapter = new TOCAdapter(this, chapters);
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
}