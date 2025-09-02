package com.example.myapplication2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
                .inflate(R.layout.book_list_item, parent, false);
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
        private ImageView bookCover;
        private TextView bookTitle;
        private TextView bookAuthor;
        private TextView bookProgress;

        public BookViewHolder(@NonNull View itemView) {
            super(itemView);
            bookCover = itemView.findViewById(R.id.bookCover);
            bookTitle = itemView.findViewById(R.id.bookTitle);
            bookAuthor = itemView.findViewById(R.id.bookAuthor);
            bookProgress = itemView.findViewById(R.id.bookProgress);
        }

        public void bind(EPUBBook book, int position) {
            bookTitle.setText(book.getTitle());
            bookAuthor.setText("作者: " + book.getAuthor());
            
            // 设置阅读进度显示
            if (book.getTotalPages() > 0) {
                String progressText = "阅读进度: 第" + (book.getCurrentPage() + 1) + "章/共" + book.getTotalPages() + "章";
                bookProgress.setText(progressText);
            } else {
                bookProgress.setText("阅读进度: 暂无");
            }
            
            // 设置封面图标（暂时使用占位符）
            bookCover.setImageResource(R.drawable.ic_launcher_foreground);
            
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