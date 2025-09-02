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

        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // 创建书籍存储目录
        booksDirectory = new File(getFilesDir(), "books");
        if (!booksDirectory.exists()) {
            booksDirectory.mkdirs();
        }

        initViews();
        setupRecyclerView();
        setupClickListeners();
        checkPermissions();
        loadSavedBooks();
    }

    private void initViews() {
        booksRecyclerView = findViewById(R.id.booksRecyclerView);
        addBookButton = findViewById(R.id.addBookButton);
    }

    private void setupRecyclerView() {
        epubBooks = new ArrayList<>();
        BooksAdapter.OnBookClickListener clickListener = new BooksAdapter.OnBookClickListener() {
            @Override
            public void onBookClick(EPUBBook book) {
                openBook(book);
            }
        };
        
        booksAdapter = new BooksAdapter(epubBooks, clickListener);
        booksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        booksRecyclerView.setAdapter(booksAdapter);
        
        // 注册长按事件监听器
        booksAdapter.setOnBookLongClickListener(new BooksAdapter.OnBookLongClickListener() {
            @Override
            public void onBookLongClick(EPUBBook book, int position) {
                showBookOptions(book, position);
            }
        });
    }

    private void setupClickListeners() {
        addBookButton.setOnClickListener(v -> openFilePicker());
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String[] mimeTypes = {"application/epub+zip"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(Intent.createChooser(intent, "选择EPUB文件"), FILE_PICKER_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予
            } else {
                Toast.makeText(this, "需要存储权限才能访问EPUB文件", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    addBookToList(uri);
                }
            }
        }
    }

    private void addBookToList(Uri uri) {
        try {
            String fileName = getFileName(uri);
            
            // 检查书籍是否已经存在于列表中
            if (isBookAlreadyAdded(fileName)) {
                Toast.makeText(this, "书籍已存在: " + fileName, Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 将书籍文件复制到应用私有目录
            String savedFileName = copyBookToPrivateStorage(uri, fileName);
            
            // 创建本地文件Uri
            File bookFile = new File(booksDirectory, savedFileName);
            Uri localUri = Uri.fromFile(bookFile);
            
            EPUBBook book = new EPUBBook(localUri, fileName);
            epubBooks.add(book);
            booksAdapter.notifyItemInserted(epubBooks.size() - 1);
            saveBooks(); // 保存书籍列表
            Toast.makeText(this, "已添加书籍: " + fileName, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "添加书籍失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    // 检查书籍是否已经添加
    private boolean isBookAlreadyAdded(String fileName) {
        for (EPUBBook book : epubBooks) {
            if (book.getTitle().equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    private String getFileName(Uri uri) {
        String fileName = System.currentTimeMillis() + ".epub"; // 默认文件名
        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(uri, null, null, null, null);
        if (cursor != null) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameIndex != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex);
            }
            cursor.close();
        }
        return fileName;
    }

    private String copyBookToPrivateStorage(Uri sourceUri, String fileName) throws IOException {
        // 确保文件名唯一
        String savedFileName = fileName;
        File targetFile = new File(booksDirectory, savedFileName);
        int counter = 1;
        
        while (targetFile.exists()) {
            int dotIndex = fileName.lastIndexOf('.');
            String nameWithoutExtension = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
            String extension = (dotIndex == -1) ? "" : fileName.substring(dotIndex);
            savedFileName = nameWithoutExtension + "_" + counter + extension;
            targetFile = new File(booksDirectory, savedFileName);
            counter++;
        }
        
        // 复制文件
        ContentResolver contentResolver = getContentResolver();
        ParcelFileDescriptor parcelFileDescriptor = contentResolver.openFileDescriptor(sourceUri, "r");
        if (parcelFileDescriptor != null) {
            FileInputStream inputStream = new FileInputStream(parcelFileDescriptor.getFileDescriptor());
            FileOutputStream outputStream = new FileOutputStream(targetFile);
            
            FileChannel inChannel = inputStream.getChannel();
            FileChannel outChannel = outputStream.getChannel();
            
            inChannel.transferTo(0, inChannel.size(), outChannel);
            
            inputStream.close();
            outputStream.close();
            parcelFileDescriptor.close();
        }
        
        return savedFileName;
    }

    private void openBook(EPUBBook book) {
        // 检查本地文件是否仍然存在
        if (isLocalFileValid(book.getUri())) {
            Intent intent = new Intent(this, ReadingActivity.class);
            intent.putExtra("book_uri", book.getUri().toString());
            intent.putExtra("book_title", book.getTitle());
            startActivity(intent);
        } else {
            Toast.makeText(this, "书籍文件不存在，请重新添加该书籍", Toast.LENGTH_LONG).show();
            // 从列表中移除无效的书籍
            removeInvalidBook(book);
        }
    }

    // 检查本地文件是否有效
    private boolean isLocalFileValid(Uri uri) {
        try {
            File file = new File(uri.getPath());
            return file.exists() && file.isFile() && file.canRead();
        } catch (Exception e) {
            return false;
        }
    }

    // 移除无效的书籍
    private void removeInvalidBook(EPUBBook book) {
        Iterator<EPUBBook> iterator = epubBooks.iterator();
        while (iterator.hasNext()) {
            EPUBBook currentBook = iterator.next();
            if (currentBook.getUri().equals(book.getUri())) {
                // 删除本地文件
                File bookFile = new File(currentBook.getUri().getPath());
                if (bookFile.exists()) {
                    bookFile.delete();
                }
                
                iterator.remove();
                saveBooks(); // 重新保存书籍列表
                booksAdapter.notifyDataSetChanged();
                break;
            }
        }
    }

    // 保存书籍列表到SharedPreferences
    private void saveBooks() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        StringBuilder bookListString = new StringBuilder();
        
        for (int i = 0; i < epubBooks.size(); i++) {
            EPUBBook book = epubBooks.get(i);
            // 格式：uri|title
            bookListString.append(book.getUri().toString())
                    .append("|")
                    .append(book.getTitle());
            
            //如果不是最后一个元素，添加分隔符
            if (i < epubBooks.size() - 1) {
                bookListString.append(";;");
            }
        }
        
        editor.putString(BOOK_LIST_KEY, bookListString.toString());
        editor.apply();
    }

    // 从SharedPreferences加载书籍列表
    private void loadSavedBooks() {
        String bookListString = sharedPreferences.getString(BOOK_LIST_KEY, "");
        if (!bookListString.isEmpty()) {
            epubBooks.clear();
            String[] bookStrings = bookListString.split(";;");
            
            for (String bookString : bookStrings) {
                String[] parts = bookString.split("\\|", 2);
                if (parts.length == 2) {
                    Uri uri = Uri.parse(parts[0]);
                    String title = parts[1];
                    // 检查本地文件是否仍然存在
                    if (isLocalFileValid(uri)) {
                        epubBooks.add(new EPUBBook(uri, title));
                    } else {
                        // 删除不存在的文件对应的条目
                        removeInvalidBookEntry(uri);
                    }
                }
            }
            
            booksAdapter.notifyDataSetChanged();
        }
    }
    
    // 移除无效书籍条目（不删除文件）
    private void removeInvalidBookEntry(Uri uri) {
        Iterator<EPUBBook> iterator = epubBooks.iterator();
        while (iterator.hasNext()) {
            EPUBBook book = iterator.next();
            if (book.getUri().equals(uri)) {
                iterator.remove();
                break;
            }
        }
    }
    
    // 显示书籍选项对话框
    private void showBookOptions(EPUBBook book, int position) {
        // 创建自定义列表项
        CharSequence[] options = {"删除"};
        
        // 创建自定义样式的对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setTitle("书籍选项")
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) { // 删除选项
                            deleteBook(book, position);
                        }
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss());
        
        // 创建并显示对话框
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    // 删除书籍
    private void deleteBook(EPUBBook book, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("确认删除")
                .setMessage("确定要删除书籍 \"" + book.getTitle() + "\" 吗？")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 删除本地文件
                        File bookFile = new File(book.getUri().getPath());
                        if (bookFile.exists()) {
                            bookFile.delete();
                        }
                        
                        // 从列表中移除
                        epubBooks.remove(position);
                        booksAdapter.notifyItemRemoved(position);
                        saveBooks(); // 重新保存书籍列表
                        
                        Toast.makeText(MainActivity.this, "书籍已删除", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}