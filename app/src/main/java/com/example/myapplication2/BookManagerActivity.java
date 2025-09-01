package com.example.myapplication2;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BookManagerActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_ADD_BOOK = 1001;
    private static final int REQUEST_CODE_PERMISSION = 1002;
    
    private ListView bookListView;
    private ArrayAdapter<String> adapter;
    private List<String> bookList;
    private File bookDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_manager);

        bookDirectory = new File(getFilesDir(), "books");
        if (!bookDirectory.exists()) {
            bookDirectory.mkdirs();
        }

        initViews();
        checkPermissions();
        loadBooks();
    }

    private void initViews() {
        bookListView = findViewById(R.id.bookListView);
        Button addBookButton = findViewById(R.id.addBookButton);
        Button refreshButton = findViewById(R.id.refreshButton);

        bookList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, bookList);
        bookListView.setAdapter(adapter);

        addBookButton.setOnClickListener(v -> openFilePicker());
        refreshButton.setOnClickListener(v -> loadBooks());

        bookListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openBook(bookList.get(position));
            }
        });
        
        // 添加长按删除功能
        bookListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showDeleteDialog(position);
                return true;
            }
        });
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE_PERMISSION);
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String[] mimeTypes = {"application/epub+zip", "application/x-zip-compressed"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(Intent.createChooser(intent, "选择EPUB文件"), REQUEST_CODE_ADD_BOOK);
        } else {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_ADD_BOOK && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                addBookToFileSystem(uri);
            }
        }
    }

    private void addBookToFileSystem(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                String fileName = getFileNameFromUri(uri);
                if (!fileName.toLowerCase().endsWith(".epub")) {
                    fileName += ".epub";
                }
                
                File bookFile = new File(bookDirectory, fileName);
                FileOutputStream outputStream = new FileOutputStream(bookFile);
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                inputStream.close();
                outputStream.close();
                
                Toast.makeText(this, "已添加书籍: " + fileName, Toast.LENGTH_SHORT).show();
                loadBooks();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "添加书籍失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
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
        
        // 如果仍然没有文件名，则使用默认名称
        if (fileName == null) {
            fileName = "book.epub";
        }
        
        return fileName;
    }

    private void loadBooks() {
        bookList.clear();
        File[] files = bookDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    bookList.add(file.getName());
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void openBook(String bookName) {
        File bookFile = new File(bookDirectory, bookName);
        if (bookFile.exists()) {
            // 打开书籍阅读器
            Intent intent = new Intent(this, BookReaderActivity.class);
            intent.putExtra("book_path", bookFile.getAbsolutePath());
            startActivity(intent);
        } else {
            Toast.makeText(this, "书籍文件不存在", Toast.LENGTH_SHORT).show();
        }
    }
    
    // 添加删除书籍功能
    private void showDeleteDialog(final int position) {
        final String bookName = bookList.get(position);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("删除书籍");
        builder.setMessage("确定要删除《" + bookName + "》吗？");
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deleteBook(position);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    private void deleteBook(int position) {
        String bookName = bookList.get(position);
        File bookFile = new File(bookDirectory, bookName);
        if (bookFile.exists() && bookFile.delete()) {
            Toast.makeText(this, "已删除书籍: " + bookName, Toast.LENGTH_SHORT).show();
            loadBooks(); // 重新加载书籍列表
        } else {
            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
        }
    }
}