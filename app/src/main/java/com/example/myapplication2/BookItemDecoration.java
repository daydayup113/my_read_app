package com.example.myapplication2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

public class BookItemDecoration extends RecyclerView.ItemDecoration {
    private final int dividerHeight;

    public BookItemDecoration(Context context) {
        Paint paint = new Paint();
        paint.setColor(ContextCompat.getColor(context, android.R.color.transparent));
        dividerHeight = context.getResources().getDimensionPixelSize(R.dimen.book_item_divider_height);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);
        outRect.bottom = dividerHeight;
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDraw(c, parent, state);
        // 由于我们使用了CardView，这里不需要绘制额外的分割线
    }
}