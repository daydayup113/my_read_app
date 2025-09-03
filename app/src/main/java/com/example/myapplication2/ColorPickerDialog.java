package com.example.myapplication2;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class ColorPickerDialog extends Dialog {
    private OnColorSelectedListener listener;
    private int selectedColor;
    private View colorPreview;
    private TextView colorHexText;
    private ColorPickerView colorPickerView;
    private Button confirmButton;
    private Button cancelButton;

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
        colorHexText = findViewById(R.id.colorHexText);
        confirmButton = findViewById(R.id.confirmButton);
        cancelButton = findViewById(R.id.cancelButton);

        // 设置按钮样式
        confirmButton.setBackgroundResource(R.drawable.button_background);
        cancelButton.setBackgroundResource(R.drawable.button_background);

        colorPickerView.setOnColorSelectedListener(new ColorPickerView.OnColorSelectedListener() {
            @Override
            public void onColorSelected(int color) {
                selectedColor = color;
                updateColorPreview();
            }
        });
    }

    private void setupListeners() {
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onColorSelected(selectedColor);
                }
                dismiss();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    private void updateColorPreview() {
        colorPreview.setBackgroundColor(selectedColor);
        
        // 更新颜色十六进制码显示
        String hexColor = String.format("#%06X", (0xFFFFFF & selectedColor));
        colorHexText.setText(hexColor);
        
        // 根据背景色调整预览文字颜色，确保可读性
        int grayValue = (int) (0.299 * Color.red(selectedColor) + 
                              0.587 * Color.green(selectedColor) + 
                              0.114 * Color.blue(selectedColor));
        
        // 如果背景较暗，使用白色文字；否则使用黑色文字
        int textColor = grayValue < 128 ? Color.WHITE : Color.BLACK;
        colorHexText.setTextColor(textColor);
    }

    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        this.listener = listener;
    }

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }
}