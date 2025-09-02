package com.example.myapplication2;

import android.net.Uri;

public class EPUBBook {
    private Uri uri;
    private String title;
    private String author;
    private int currentPage;
    private int totalPages;

    public EPUBBook(Uri uri, String title) {
        this.uri = uri;
        this.title = title;
        this.author = "未知作者";
        this.currentPage = 0;
        this.totalPages = 0;
    }

    public EPUBBook(Uri uri, String title, String author, int currentPage, int totalPages) {
        this.uri = uri;
        this.title = title;
        this.author = author;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
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
}