package com.example.myapplication2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
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
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int FILE_PICKER_REQUEST_CODE = 2;
    public static final String PREFS_NAME = "BookList";
    private static final String BOOK_LIST_KEY = "books";

    private RecyclerView booksRecyclerView;
    private BooksAdapter booksAdapter;
    private List<EPUBBook> epubBooks;
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
        Button addBookButton = findViewById(R.id.addBookButton);
        
        addBookButton.setOnClickListener(v -> {
            if (checkPermissions()) {
                openFilePicker();
            }
        });
    }

    private void setupRecyclerView() {
        booksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        // 处理书籍点击事件
        booksAdapter = new BooksAdapter(epubBooks);
        booksAdapter.setOnBookClickListener(this::openBook);
        booksAdapter.setOnBookLongClickListener(this::showBookOptions);
        
        booksRecyclerView.setAdapter(booksAdapter);
        
        // 添加装饰器
        booksRecyclerView.addItemDecoration(new BookItemDecoration(this));
    }

    // 显示书籍选项菜单
    private void showBookOptions(EPUBBook book, int position) {
        String[] options = {"删除", "编辑信息"};
        new AlertDialog.Builder(this)
                .setTitle("书籍选项")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // 删除
                            showDeleteDialog(book, position);
                            break;
                        case 1: // 编辑信息
                            showEditDialog(book, position);
                            break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 显示删除对话框
    private void showDeleteDialog(EPUBBook book, int position) {
        new AlertDialog.Builder(this)
                .setTitle("删除书籍")
                .setMessage("确定要删除《" + book.getTitle() + "》吗？")
                .setPositiveButton("删除", (dialog, which) -> deleteBook(book, position))
                .setNegativeButton("取消", null)
                .show();
    }

    // 显示编辑对话框
    private void showEditDialog(EPUBBook book, int position) {
        // 创建自定义布局的对话框
        View dialogView = getLayoutInflater().inflate(R.layout.edit_book_dialog, null);
        android.widget.EditText titleEditText = dialogView.findViewById(R.id.editTextTitle);
        android.widget.EditText authorEditText = dialogView.findViewById(R.id.editTextAuthor);
        
        titleEditText.setText(book.getTitle());
        authorEditText.setText(book.getAuthor());
        
        new AlertDialog.Builder(this)
                .setTitle("编辑书籍信息")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newTitle = titleEditText.getText().toString().trim();
                    String newAuthor = authorEditText.getText().toString().trim();
                    
                    if (!newTitle.isEmpty()) {
                        // 如果标题发生了变化，需要重命名文件
                        String oldFileName = book.getFileName();
                        String newFileName = newTitle + ".epub";
                        
                        if (!newFileName.equals(oldFileName)) {
                            // 重命名本地文件
                            File oldFile = new File(booksDirectory, oldFileName);
                            File newFile = new File(booksDirectory, newFileName);
                            
                            if (oldFile.exists()) {
                                if (!newFile.exists()) {
                                    if (oldFile.renameTo(newFile)) {
                                        // 更新书籍信息
                                        book.setTitle(newTitle);
                                        book.setAuthor(newAuthor);
                                        book.setFileName(newFileName);
                                        
                                        // 更新URI为新的本地文件URI
                                        Uri newUri = Uri.fromFile(newFile);
                                        book.setUri(newUri);
                                        
                                        // 更新显示
                                        booksAdapter.notifyItemChanged(position);
                                        // 保存更新后的书籍列表
                                        saveBooks();
                                        Toast.makeText(this, "书籍信息已更新", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(this, "文件重命名失败", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Toast.makeText(this, "已存在同名书籍", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(this, "原文件不存在", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // 只更新作者信息，但也需要更新标题（用户可能只是编辑了标题但没有改变文件名）
                            book.setTitle(newTitle);
                            book.setAuthor(newAuthor);
                            // 更新显示
                            booksAdapter.notifyItemChanged(position);
                            // 保存更新后的书籍列表
                            saveBooks();
                            Toast.makeText(this, "书籍信息已更新", Toast.LENGTH_SHORT).show();
                        }
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
    @SuppressLint("NotifyDataSetChanged")
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
        Collections.sort(epubBooks, (book1, book2) -> {
            // 按最后阅读时间降序排列（最近阅读的在前）
            return Long.compare(book2.getLastReadTime(), book1.getLastReadTime());
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

    @SuppressLint("NotifyDataSetChanged")
    private void handleSelectedFile(Uri uri) {
        try {
            // 请求持久的URI权限
            getContentResolver().takePersistableUriPermission(uri, 
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
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
            
            // 创建EPUBBook对象，使用本地文件的URI而不是原始URI
            Uri localUri = Uri.fromFile(destFile);
            // 新添加的书籍初始化当前页为0，总页数为0，最后阅读时间为当前时间
            EPUBBook book = new EPUBBook(localUri, fileName, "未知作者", 0, 0, System.currentTimeMillis(), fileName);
            epubBooks.add(book);
            
            // 更新列表显示
            sortBooksByLastReadTime();
            booksAdapter.notifyDataSetChanged();
            
            // 保存书籍列表
            saveBooks();
            
            Toast.makeText(this, "书籍添加成功", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Log.e(TAG, "权限错误: " + e.getMessage());
            Toast.makeText(this, "权限不足，无法访问文件: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.d(TAG,"错误日志====handleSelectedFile 249");
            Toast.makeText(this, "添加书籍失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyFile(Uri sourceUri, File destFile) throws IOException {
        ParcelFileDescriptor sourcePFD = getContentResolver().openFileDescriptor(sourceUri, "r");
        assert sourcePFD != null;
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

    @SuppressLint("NotifyDataSetChanged")
    private void loadSavedBooks() {
        String booksJson = sharedPreferences.getString(BOOK_LIST_KEY, "");
        boolean booksChanged = false;
        if (!booksJson.isEmpty()) {
            epubBooks.clear();
            String[] bookStrings = booksJson.split(";");
            for (String bookString : bookStrings) {
                if (!bookString.isEmpty()) {
                    String[] parts = bookString.split("\\|");
                    if (parts.length >= 6) {
                        try {
                            // 注意：这里我们不再使用保存的URI，而是直接使用本地文件构建URI
                            String title = parts[1];
                            String author = parts[2];
                            int savedCurrentPage = Integer.parseInt(parts[3]); // 保存的当前页
                            int savedTotalPages = Integer.parseInt(parts[4]);   // 保存的总页数
                            long lastReadTime = Long.parseLong(parts[5]);
                            String fileName = (parts.length > 6) ? parts[6] : "unknown.epub";
                            String lastChapter = (parts.length > 7) ? parts[7] : ""; // 最后阅读章节
                            String finalChapter = (parts.length > 8) ? parts[8] : ""; // 最后一章
                            
                            // 检查本地文件是否存在
                            java.io.File bookFile = new java.io.File(booksDirectory, fileName);
                            if (bookFile.exists()) {
                                // 使用本地文件路径创建URI
                                android.net.Uri localUri = android.net.Uri.fromFile(bookFile);
                                
                                // 从ReadingActivity的SharedPreferences中获取最新的当前页和总页数
                                android.content.SharedPreferences readingPrefs = getSharedPreferences("ReadingProgress", android.content.Context.MODE_PRIVATE);
                                int currentPage = readingPrefs.getInt(localUri.toString(), savedCurrentPage);
                                int totalPages = readingPrefs.getInt(localUri.toString() + "_total", savedTotalPages);
                                
                                // 从ReadingActivity的SharedPreferences中获取最后阅读章节
                                String savedLastChapter = readingPrefs.getString(localUri.toString() + "_lastChapter", lastChapter);
                                
                                EPUBBook book = new EPUBBook(localUri, title, author, currentPage, totalPages, lastReadTime, fileName, savedLastChapter, finalChapter);
                                epubBooks.add(book);
                            } else {
                                // 文件不存在，标记为需要更新
                                booksChanged = true;
                            }
                        } catch (Exception e) {
                            android.util.Log.d(TAG,"错误日志====handleSelectedFile 249"+e);
                            booksChanged = true;
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
        
        // 如果有书籍被移除，则重新保存书籍列表
        if (booksChanged) {
            saveBooks();
        }
    }

    private void saveBooks() {
        StringBuilder booksStringBuilder = new StringBuilder();
        for (EPUBBook book : epubBooks) {
            if (book != null) {
                booksStringBuilder.append(book.getUri() != null ? book.getUri().toString() : "")
                        .append("|")
                        .append(book.getTitle() != null ? book.getTitle() : "未知标题")
                        .append("|")
                        .append(book.getAuthor() != null ? book.getAuthor() : "未知作者")
                        .append("|")
                        .append(book.getCurrentPage())
                        .append("|")
                        .append(book.getTotalPages())
                        .append("|")
                        .append(book.getLastReadTime())
                        .append("|")
                        .append(book.getFileName() != null ? book.getFileName() : "unknown.epub")
                        .append("|")
                        .append(book.getLastChapter() != null ? book.getLastChapter() : "") // 保存最后阅读章节
                        .append("|")
                        .append(book.getFinalChapter() != null ? book.getFinalChapter() : "") // 保存最后一章
                        .append(";");
            }
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