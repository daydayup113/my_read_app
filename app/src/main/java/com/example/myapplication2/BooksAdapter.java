package com.example.myapplication2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BooksAdapter extends RecyclerView.Adapter<BooksAdapter.BookViewHolder> {
    private List<EPUBBook> books;
    private OnBookClickListener listener;
    private OnBookLongClickListener longClickListener;

    public BooksAdapter(List<EPUBBook> books, OnBookClickListener listener) {
        this.books = books;
        this.listener = listener;
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        EPUBBook book = books.get(position);
        holder.bind(book, position);
    }

    @Override
    public int getItemCount() {
        return books.size();
    }
    
    // 添加长按事件监听器设置方法
    public void setOnBookLongClickListener(OnBookLongClickListener listener) {
        this.longClickListener = listener;
    }

    public class BookViewHolder extends RecyclerView.ViewHolder {
        private TextView textView;

        public BookViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }

        public void bind(EPUBBook book, int position) {
            textView.setText(book.getTitle());
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBookClick(book);
                }
            });
            
            // 添加长按事件处理
            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onBookLongClick(book, position);
                }
                return true;
            });
        }
    }

    public interface OnBookClickListener {
        void onBookClick(EPUBBook book);
    }
    
    // 添加长按事件接口
    public interface OnBookLongClickListener {
        void onBookLongClick(EPUBBook book, int position);
    }
}