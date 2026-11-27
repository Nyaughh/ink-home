package dev.inkhome;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.TextView;

/** Displays the PNG produced by the ordinary sleep-screen settings. */
public final class SleepProbeDream extends DreamService {
    private ImageView current;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFullscreen(true);
        setInteractive(false);
        ImageView view = new ImageView(this);
        current = view;
        view.setBackgroundColor(Color.WHITE);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        setContentView(view);
        SleepSize size = SleepScreen.panel(this);
        new Thread(() -> {
            Bitmap image = SleepScreen.preview(SleepScreen.file(), size.width, size.height);
            getMainExecutor().execute(() -> {
                if (current != view) return;
                if (image != null) {
                    view.setImageBitmap(image);
                } else {
                    TextView missing = new TextView(this);
                    missing.setGravity(Gravity.CENTER);
                    missing.setTextColor(Color.BLACK);
                    missing.setBackgroundColor(Color.WHITE);
                    missing.setText("Sleep probe: generated sleep picture unavailable");
                    setContentView(missing);
                }
            });
        }, "sleep-probe-picture").start();
    }

    @Override public void onDetachedFromWindow() {
        current = null;
        super.onDetachedFromWindow();
    }
}
