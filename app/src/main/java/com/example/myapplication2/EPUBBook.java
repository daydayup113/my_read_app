package com.example.myapplication2;

import android.net.Uri;

public class EPUBBook {
    private Uri uri;
    private String title;

    public EPUBBook(Uri uri, String title) {
        this.uri = uri;
        this.title = title;
    }

    public Uri getUri() {
        return uri;
    }

    public String getTitle() {
        return title;
    }
}