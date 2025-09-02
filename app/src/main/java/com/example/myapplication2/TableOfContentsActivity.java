package com.example.myapplication2;

import android.os.Bundle;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import nl.siegmann.epublib.domain.TOCReference;
import nl.siegmann.epublib.domain.TableOfContents;

public class TableOfContentsActivity extends AppCompatActivity {
    private static final String TAG = "TOCActivity";
    private ListView tocListView;
    private TOCAdapter tocAdapter;
    private List<TOCReference> chapters;

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
            // 在实际应用中，这里应该跳转到指定章节
            TOCReference chapter = chapters.get(position);
            String title = chapter.getTitle();
            Toast.makeText(this, "跳转到章节: " + title, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void loadTableOfContents() {
        // 在实际应用中，这里应该从EPUB文件中加载真实的章节目录
        // 由于这是一个演示，我们创建一些示例数据
        try {
            for (int i = 1; i <= 10; i++) {
                // 创建模拟的TOCReference对象
                chapters.add(new TOCReference("第 " + i + " 章 - 示例章节标题 " + i, null));
            }
            tocAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            Log.e(TAG, "加载目录时出错", e);
            Toast.makeText(this, "加载目录时出错: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}