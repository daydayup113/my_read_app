package com.example.myapplication2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BooksAdapter extends RecyclerView.Adapter<BooksAdapter.BookViewHolder> {
    
    private List<EPUBBook> books;
    private OnBookClickListener listener;
    private OnBookLongClickListener longClickListener;
    
    public interface OnBookClickListener {
        void onBookClick(EPUBBook book);
    }
    
    public interface OnBookLongClickListener {
        void onBookLongClick(EPUBBook book, int position);
    }
    
    public BooksAdapter(List<EPUBBook> books) {
        this.books = books;
    }
    
    public void setOnBookClickListener(OnBookClickListener listener) {
        this.listener = listener;
    }
    
    public void setOnBookLongClickListener(OnBookLongClickListener listener) {
        this.longClickListener = listener;
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
        holder.bind(books.get(position), position);
    }
    
    @Override
    public int getItemCount() {
        return books != null ? books.size() : 0;
    }
    
    public class BookViewHolder extends RecyclerView.ViewHolder {
        private ImageView bookCover;
        private TextView bookTitle;
        private TextView bookAuthor;
        private TextView lastChapter;
        private TextView finalChapter;
        
        public BookViewHolder(@NonNull View itemView) {
            super(itemView);
            bookCover = itemView.findViewById(R.id.bookCover);
            bookTitle = itemView.findViewById(R.id.bookTitle);
            bookAuthor = itemView.findViewById(R.id.bookAuthor);
            lastChapter = itemView.findViewById(R.id.lastChapter);
            finalChapter = itemView.findViewById(R.id.finalChapter);
        }

        @android.annotation.SuppressLint("SetTextI18n")
        public void bind(EPUBBook book, int position) {
            // 第一行显示书籍名
            bookTitle.setText(book.getTitle());
            
            // 第二行显示作者名
            bookAuthor.setText("作者: " + book.getAuthor());
            
            // 第三行显示最后阅读的章节序号以及章节名
            if (book.getLastChapter() != null && !book.getLastChapter().isEmpty()) {
                String lastChapterText = "最后阅读: " + book.getLastChapter();
                lastChapter.setText(lastChapterText);
            } else if (book.getTotalPages() > 0 && book.getCurrentPage() >= 0) {
                // 如果没有保存的最后阅读章节，则使用当前页作为最后阅读章节
                String currentChapterText = "最后阅读: 第" + (book.getCurrentPage() + 1) + "章";
                lastChapter.setText(currentChapterText);
            } else {
                lastChapter.setText("最后阅读: 无章节信息");
            }
            
            // 第四行显示书籍最后一章的章节序号以及章节名
            if (book.getFinalChapter() != null && !book.getFinalChapter().isEmpty()) {
                String finalChapterText = "最后一章: " + book.getFinalChapter();
                finalChapter.setText(finalChapterText);
            } else if (book.getTotalPages() > 0) {
                // 如果没有保存的最后一章，则显示总章节数
                String finalChapterText = "最后一章: 第" + book.getTotalPages() + "章";
                finalChapter.setText(finalChapterText);
            } else {
                finalChapter.setText("最后一章: 暂无");
            }
            
            // 设置封面图标（使用书籍封面图标）
            bookCover.setImageResource(R.drawable.ic_book_cover);
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBookClick(book);
                }
            });
            
            // 添加长按事件监听
            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onBookLongClick(book, position);
                    return true;
                }
                return false;
            });
        }
    }
}