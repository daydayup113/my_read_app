package com.example.myapplication2;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BooksAdapter extends RecyclerView.Adapter<BooksAdapter.BookViewHolder> {
    private final List<EPUBBook> books;
    private final OnBookClickListener listener;
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
        private final ImageView bookCover;
        private final TextView bookTitle;
        private final TextView bookAuthor;
        private final TextView bookProgress;
        private final TextView lastReadTime; // 添加最后阅读时间显示

        public BookViewHolder(@NonNull View itemView) {
            super(itemView);
            bookCover = itemView.findViewById(R.id.bookCover);
            bookTitle = itemView.findViewById(R.id.bookTitle);
            bookAuthor = itemView.findViewById(R.id.bookAuthor);
            bookProgress = itemView.findViewById(R.id.bookProgress);
            lastReadTime = itemView.findViewById(R.id.lastReadTime); // 初始化最后阅读时间显示
        }

        @SuppressLint("SetTextI18n")
        public void bind(EPUBBook book, int position) {
            bookTitle.setText(book.getTitle());
            bookAuthor.setText("作者: " + book.getAuthor());
            
            // 设置阅读进度显示
            if (book.getTotalPages() > 0) {
                String progressText = "阅读进度: 第" + (book.getCurrentPage() + 1) + "章/共" + book.getTotalPages() + "章";
                bookProgress.setText(progressText);
                // 根据阅读进度设置颜色
                int progressPercent = (int) (((float) (book.getCurrentPage() + 1) / book.getTotalPages()) * 100);
                if (progressPercent < 30) {
                    bookProgress.setTextColor(itemView.getContext().getResources().getColor(android.R.color.holo_red_dark));
                } else if (progressPercent < 70) {
                    bookProgress.setTextColor(itemView.getContext().getResources().getColor(android.R.color.holo_orange_dark));
                } else {
                    bookProgress.setTextColor(itemView.getContext().getResources().getColor(android.R.color.holo_green_dark));
                }
            } else {
                bookProgress.setText("阅读进度: 暂无");
                bookProgress.setTextColor(itemView.getContext().getResources().getColor(android.R.color.darker_gray));
            }
            
            // 设置最后阅读时间显示
            if (book.getLastReadTime() > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                String timeText = "最后阅读: " + sdf.format(new Date(book.getLastReadTime()));
                lastReadTime.setText(timeText);
                lastReadTime.setVisibility(View.VISIBLE);
                
                // 根据最后阅读时间设置颜色（最近阅读的用深色，较久的用浅色）
                long timeDiff = System.currentTimeMillis() - book.getLastReadTime();
                long daysDiff = timeDiff / (24 * 60 * 60 * 1000);
                if (daysDiff < 1) {
                    lastReadTime.setTextColor(itemView.getContext().getResources().getColor(android.R.color.holo_blue_dark));
                } else if (daysDiff < 7) {
                    lastReadTime.setTextColor(itemView.getContext().getResources().getColor(android.R.color.holo_blue_light));
                } else {
                    lastReadTime.setTextColor(itemView.getContext().getResources().getColor(android.R.color.darker_gray));
                }
            } else {
                lastReadTime.setText("最后阅读: 从未");
                lastReadTime.setTextColor(itemView.getContext().getResources().getColor(android.R.color.darker_gray));
                lastReadTime.setVisibility(View.VISIBLE);
            }
            
            // 设置封面图标（使用书籍封面图标）
            bookCover.setImageResource(R.drawable.ic_book_cover);
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBookClick(book);
                }
            });
            
            // 添加长按事件处理
            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onBookLongClick(book, position);
                    return true;
                }
                return false;
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