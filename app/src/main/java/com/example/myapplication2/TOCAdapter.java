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
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false);
        }

        TextView textView = convertView.findViewById(android.R.id.text1);
        TOCReference chapter = chapters.get(position);
        textView.setText(chapter.getTitle());
        textView.setTextSize(18);

        return convertView;
    }
}