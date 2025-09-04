package com.example.myapplication2;

import android.net.Uri;

public class EPUBBook {
    private final Uri uri;
    private final String title;
    private String author;
    private int currentPage;
    private int totalPages;
    private long lastReadTime; // 添加最后阅读时间字段
    private String fileName; // 添加文件名字段

    public EPUBBook(Uri uri, String title) {
        this.uri = uri;
        this.title = title;
        this.author = "未知作者";
        this.currentPage = 0;
        this.totalPages = 0;
        this.lastReadTime = 0; // 默认为0
        this.fileName = "unknown.epub"; // 默认文件名
    }

    public EPUBBook(Uri uri, String title, String author, int currentPage, int totalPages) {
        this.uri = uri;
        this.title = title;
        this.author = author;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.lastReadTime = 0; // 默认为0
        this.fileName = "unknown.epub"; // 默认文件名
    }

    // 新增构造函数，包含最后阅读时间
    public EPUBBook(Uri uri, String title, String author, int currentPage, int totalPages, long lastReadTime) {
        this.uri = uri;
        this.title = title;
        this.author = author;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.lastReadTime = lastReadTime;
        this.fileName = "unknown.epub"; // 默认文件名
    }
    
    // 新增构造函数，包含最后阅读时间和文件名
    public EPUBBook(Uri uri, String title, String author, int currentPage, int totalPages, long lastReadTime, String fileName) {
        this.uri = uri;
        this.title = title;
        this.author = author;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.lastReadTime = lastReadTime;
        this.fileName = fileName;
    }

    public Uri getUri() {
        return uri;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
    
    // 添加最后阅读时间的getter和setter方法
    public long getLastReadTime() {
        return lastReadTime;
    }
    
    public void setLastReadTime(long lastReadTime) {
        this.lastReadTime = lastReadTime;
    }
    
    // 添加文件名的getter和setter方法
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}