package com.example.myapplication2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class ColorPickerView extends View {
    private Paint paint;
    private Paint centerPaint;
    private Paint touchPaint;
    private int[] colors;
    private boolean isTracking = false;
    private OnColorSelectedListener listener;
    private float centerX, centerY;
    private float radius;
    private float touchX, touchY;
    private float strokeWidth = 50f;

    public ColorPickerView(Context context) {
        super(context);
        init();
    }

    public ColorPickerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorPickerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 初始化颜色数组，创建彩虹色环
        colors = new int[]{
                Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN,
                Color.GREEN, Color.YELLOW, Color.RED
        };

        // 绘制色环的画笔
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);

        // 绘制中心颜色的画笔
        centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(Color.RED);

        // 绘制触摸指示器的画笔
        touchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        touchPaint.setStyle(Paint.Style.STROKE);
        touchPaint.setStrokeWidth(5f);
        touchPaint.setColor(Color.WHITE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // 计算中心点和半径
        centerX = w / 2f;
        centerY = h / 2f;
        // 确保色环不会超出视图边界，留出适当边距
        radius = Math.min(w, h) / 2f - strokeWidth;
        
        // 确保半径为正数
        if (radius < 0) {
            radius = strokeWidth;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 创建色环渐变
        Shader shader = new SweepGradient(centerX, centerY, colors, null);
        paint.setShader(shader);

        // 绘制色环
        canvas.drawCircle(centerX, centerY, radius, paint);

        // 绘制中心颜色区域
        canvas.drawCircle(centerX, centerY, radius / 3f, centerPaint);

        // 绘制触摸指示器
        if (touchX > 0 && touchY > 0) {
            canvas.drawCircle(touchX, touchY, 20f, touchPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (isInColorWheel(x, y)) {
                    isTracking = true;
                    updateColor(x, y);
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (isTracking) {
                    updateColor(x, y);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (isTracking) {
                    isTracking = false;
                    updateColor(x, y);
                    return true;
                }
                break;
        }

        return super.onTouchEvent(event);
    }

    private boolean isInColorWheel(float x, float y) {
        // 判断触摸点是否在色环内
        float dx = x - centerX;
        float dy = y - centerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        // 考虑笔触宽度，扩大检测范围
        return distance <= radius + strokeWidth/2 && distance >= radius - strokeWidth/2;
    }

    private void updateColor(float x, float y) {
        // 计算触摸点的角度
        float dx = x - centerX;
        float dy = y - centerY;
        double angle = Math.atan2(dy, dx);
        
        // 将角度转换为0-360度
        float degrees = (float) Math.toDegrees(angle);
        if (degrees < 0) {
            degrees += 360f;
        }
        
        // 根据角度计算颜色
        int color = getColorAtAngle(degrees);
        centerPaint.setColor(color);
        
        // 更新触摸点位置
        touchX = x;
        touchY = y;
        
        // 通知颜色选择监听器
        if (listener != null) {
            listener.onColorSelected(color);
        }
        
        // 重绘视图
        invalidate();
    }

    private int getColorAtAngle(float angle) {
        // 根据角度在色环中插值计算颜色
        float unit = angle / (360f / (colors.length - 1));
        int i = (int) unit;
        float remainder = unit - i;
        
        if (i >= colors.length - 1) {
            return colors[colors.length - 1];
        }
        
        int c0 = colors[i];
        int c1 = colors[i + 1];
        
        // 在两个颜色之间插值
        int a = (int) (Color.alpha(c0) + remainder * (Color.alpha(c1) - Color.alpha(c0)));
        int r = (int) (Color.red(c0) + remainder * (Color.red(c1) - Color.red(c0)));
        int g = (int) (Color.green(c0) + remainder * (Color.green(c1) - Color.green(c0)));
        int b = (int) (Color.blue(c0) + remainder * (Color.blue(c1) - Color.blue(c0)));
        
        return Color.argb(a, r, g, b);
    }

    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        this.listener = listener;
    }

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }
}