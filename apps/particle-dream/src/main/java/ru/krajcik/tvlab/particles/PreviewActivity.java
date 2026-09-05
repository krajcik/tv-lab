package ru.krajcik.tvlab.particles;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

public final class PreviewActivity extends Activity {
    private ParticleView view;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        view = new ParticleView(this);
        setContentView(view);
    }

    @Override public void onResume() { super.onResume(); view.onResume(); }
    @Override public void onPause() { view.onPause(); super.onPause(); }
}
