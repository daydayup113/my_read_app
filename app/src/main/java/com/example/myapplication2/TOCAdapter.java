package com.example.myapplication2;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import nl.siegmann.epublib.domain.TOCReference;

public class TOCAdapter extends BaseAdapter {
    private Context context;
    private List<TOCReference> chapters;

    public TOCAdapter(Context context, List<TOCReference> chapters) {
        this.context = context;
        this.chapters = chapters;
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
        holder.chapterTitle.setText((position + 1) + ". " + chapter.getTitle());

        return convertView;
    }
    
    static class ViewHolder {
        TextView chapterTitle;
    }
}