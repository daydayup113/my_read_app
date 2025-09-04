package com.example.myapplication2;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import nl.siegmann.epublib.domain.TOCReference;

public class TOCAdapter extends BaseAdapter {
    private static final String TAG = "TOCAdapter";
    private Context context;
    private List<TOCReference> chapters;
    private int currentChapterPosition; // 当前章节位置

    public TOCAdapter(Context context, List<TOCReference> chapters, int currentChapterPosition) {
        this.context = context;
        this.chapters = chapters;
        this.currentChapterPosition = currentChapterPosition;
        Log.d(TAG, "TOCAdapter created with currentChapterPosition: " + currentChapterPosition);
    }

    @Override
    public int getCount() {
        return chapters.size();
    }

    @Override
    public Object getItem(int position) {
        return chapters.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.toc_item, parent, false);
            holder = new ViewHolder();
            holder.chapterTitle = convertView.findViewById(R.id.chapterTitle);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        TOCReference chapter = chapters.get(position);
        // 设置章节编号和标题
        String chapterText = (position + 1) + ". " + chapter.getTitle();
        holder.chapterTitle.setText(chapterText);
        
        // 如果是当前章节，改变字体颜色和样式
        if (position == currentChapterPosition) {
            Log.d(TAG, "Highlighting current chapter: " + chapterText + " at position " + position);
            holder.chapterTitle.setTextColor(Color.BLUE); // 当前章节使用蓝色
            holder.chapterTitle.setTypeface(null, Typeface.BOLD); // 设置粗体
        } else {
            holder.chapterTitle.setTextColor(Color.BLACK); // 其他章节使用黑色
            holder.chapterTitle.setTypeface(null, Typeface.NORMAL); // 取消粗体
        }

        return convertView;
    }
    
    static class ViewHolder {
        TextView chapterTitle;
    }
}