package com.example.myapplication2;

import android.Manifest;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int FILE_PICKER_REQUEST_CODE = 2;
    private static final String PREFS_NAME = "BookList";
    private static final String BOOK_LIST_KEY = "books";

    private RecyclerView booksRecyclerView;
    private BooksAdapter booksAdapter;
    private List<EPUBBook> epubBooks;
    private Button addBookButton;
    private SharedPreferences sharedPreferences;
    private File booksDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化视图
        initViews();

        // 初始化书籍列表
        epubBooks = new ArrayList<>();
        
        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // 获取应用私有目录下的books文件夹
        booksDirectory = new File(getFilesDir(), "books");
        if (!booksDirectory.exists()) {
            booksDirectory.mkdirs();
        }

        // 设置RecyclerView
        setupRecyclerView();

        // 加载已保存的书籍
        loadSavedBooks();

        // 检查并请求存储权限
        checkPermissions();
    }

    private void initViews() {
        booksRecyclerView = findViewById(R.id.booksRecyclerView);
        addBookButton = findViewById(R.id.addBookButton);
        
        addBookButton.setOnClickListener(v -> {
            if (checkPermissions()) {
                openFilePicker();
            }
        });
    }

    private void setupRecyclerView() {
        booksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        booksAdapter = new BooksAdapter(epubBooks, book -> {
            // 处理书籍点击事件
            openBook(book);
        });
        
        // 设置长按事件监听器
        booksAdapter.setOnBookLongClickListener((book, position) -> {
            // 处理书籍长按事件，显示删除选项
            showDeleteDialog(book, position);
        });
        
        booksRecyclerView.setAdapter(booksAdapter);
        
        // 添加分割线
        booksRecyclerView.addItemDecoration(new BookItemDecoration(this));
    }

    // 显示删除对话框
    private void showDeleteDialog(EPUBBook book, int position) {
        new AlertDialog.Builder(this)
                .setTitle("删除书籍")
                .setMessage("确定要删除《" + book.getTitle() + "》吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteBook(book, position);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 删除书籍
    private void deleteBook(EPUBBook book, int position) {
        // 从列表中移除
        epubBooks.remove(position);
        booksAdapter.notifyItemRemoved(position);
        
        // 删除存储的文件
        File bookFile = new File(booksDirectory, book.getFileName());
        if (bookFile.exists()) {
            bookFile.delete();
        }
        
        // 保存更新后的书籍列表
        saveBooks();
        
        Toast.makeText(this, "书籍已删除", Toast.LENGTH_SHORT).show();
    }

    // 打开书籍
    private void openBook(EPUBBook book) {
        // 更新最后阅读时间
        book.setLastReadTime(System.currentTimeMillis());
        sortBooksByLastReadTime();
        booksAdapter.notifyDataSetChanged();
        saveBooks();
        
        Intent intent = new Intent(this, ReadingActivity.class);
        intent.putExtra("book_uri", book.getUri().toString());
        intent.putExtra("book_title", book.getTitle());
        startActivity(intent);
    }
    
    // 按最后阅读时间排序书籍
    private void sortBooksByLastReadTime() {
        Collections.sort(epubBooks, new Comparator<EPUBBook>() {
            @Override
            public int compare(EPUBBook book1, EPUBBook book2) {
                // 按最后阅读时间降序排列（最近阅读的在前）
                return Long.compare(book2.getLastReadTime(), book1.getLastReadTime());
            }
        });
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFilePicker();
            } else {
                Toast.makeText(this, "需要存储权限才能添加书籍", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimetypes = {"application/epub+zip", "application/x-epub"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
        startActivityForResult(intent, FILE_PICKER_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    handleSelectedFile(uri);
                }
            }
        }
    }

    private void handleSelectedFile(Uri uri) {
        try {
            ContentResolver contentResolver = getContentResolver();
            Cursor cursor = contentResolver.query(uri, null, null, null, null);
            String fileName = "unknown.epub";
            
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }
            
            // 检查书籍是否已存在
            boolean bookExists = false;
            for (EPUBBook book : epubBooks) {
                if (book.getFileName().equals(fileName)) {
                    bookExists = true;
                    break;
                }
            }
            
            if (bookExists) {
                Toast.makeText(this, "书籍已存在", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 复制文件到应用私有目录
            File destFile = new File(booksDirectory, fileName);
            copyFile(uri, destFile);
            
            // 创建EPUBBook对象
            EPUBBook book = new EPUBBook(uri, fileName, "未知作者", 0, 0, System.currentTimeMillis(), fileName);
            epubBooks.add(book);
            
            // 更新列表显示
            sortBooksByLastReadTime();
            booksAdapter.notifyDataSetChanged();
            
            // 保存书籍列表
            saveBooks();
            
            Toast.makeText(this, "书籍添加成功", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "添加书籍失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyFile(Uri sourceUri, File destFile) throws IOException {
        ParcelFileDescriptor sourcePFD = getContentResolver().openFileDescriptor(sourceUri, "r");
        FileInputStream inputStream = new FileInputStream(sourcePFD.getFileDescriptor());
        FileOutputStream outputStream = new FileOutputStream(destFile);
        FileChannel sourceChannel = inputStream.getChannel();
        FileChannel destChannel = outputStream.getChannel();
        sourceChannel.transferTo(0, sourceChannel.size(), destChannel);
        sourceChannel.close();
        destChannel.close();
        inputStream.close();
        outputStream.close();
        sourcePFD.close();
    }

    private void loadSavedBooks() {
        String booksJson = sharedPreferences.getString(BOOK_LIST_KEY, "");
        if (!booksJson.isEmpty()) {
            epubBooks.clear();
            String[] bookStrings = booksJson.split(";");
            for (String bookString : bookStrings) {
                if (!bookString.isEmpty()) {
                    String[] parts = bookString.split("\\|");
                    if (parts.length >= 6) {
                        try {
                            Uri uri = Uri.parse(parts[0]);
                            String title = parts[1];
                            String author = parts[2];
                            int currentPage = Integer.parseInt(parts[3]);
                            int totalPages = Integer.parseInt(parts[4]);
                            long lastReadTime = Long.parseLong(parts[5]);
                            String fileName = (parts.length > 6) ? parts[6] : "unknown.epub";
                            
                            // 检查文件是否存在
                            File bookFile = new File(booksDirectory, fileName);
                            if (bookFile.exists()) {
                                EPUBBook book = new EPUBBook(uri, title, author, currentPage, totalPages, lastReadTime, fileName);
                                epubBooks.add(book);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        
        // 按最后阅读时间排序
        sortBooksByLastReadTime();
        
        if (booksAdapter != null) {
            booksAdapter.notifyDataSetChanged();
        }
    }

    private void saveBooks() {
        StringBuilder booksStringBuilder = new StringBuilder();
        for (EPUBBook book : epubBooks) {
            booksStringBuilder.append(book.getUri().toString())
                    .append("|")
                    .append(book.getTitle())
                    .append("|")
                    .append(book.getAuthor())
                    .append("|")
                    .append(book.getCurrentPage())
                    .append("|")
                    .append(book.getTotalPages())
                    .append("|")
                    .append(book.getLastReadTime())
                    .append("|")
                    .append(book.getFileName())
                    .append(";");
        }
        
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(BOOK_LIST_KEY, booksStringBuilder.toString());
        editor.apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSavedBooks();
    }
}