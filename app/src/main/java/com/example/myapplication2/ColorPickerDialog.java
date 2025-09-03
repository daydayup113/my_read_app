package com.example.myapplication2;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

public class ColorPickerDialog extends Dialog {
    private OnColorSelectedListener listener;
    private int selectedColor;
    private View colorPreview;
    private ColorPickerView colorPickerView;

    public ColorPickerDialog(Context context, int initialColor) {
        super(context);
        initializeDialog(context, initialColor);
    }

    private void initializeDialog(Context context, int initialColor) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.color_picker_dialog);

        selectedColor = initialColor;

        initViews();
        setupListeners();
        updateColorPreview();

        // 设置窗口背景为透明
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            
            // 调整窗口布局参数，确保对话框正确显示
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(getWindow().getAttributes());
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            getWindow().setAttributes(layoutParams);
        }
    }

    private void initViews() {
        colorPickerView = findViewById(R.id.colorPickerView);
        colorPreview = findViewById(R.id.colorPreview);

        colorPickerView.setOnColorSelectedListener(new ColorPickerView.OnColorSelectedListener() {
            @Override
            public void onColorSelected(int color) {
                selectedColor = color;
                updateColorPreview();
            }
        });
    }

    private void setupListeners() {
        findViewById(R.id.confirmButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onColorSelected(selectedColor);
                }
                dismiss();
            }
        });

        findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    private void updateColorPreview() {
        colorPreview.setBackgroundColor(selectedColor);
    }

    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        this.listener = listener;
    }

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }
}