package ru.krajcik.tvlab.particles;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

public final class PreviewActivity extends Activity {
    private ParticleSession session;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        DisplaySetup.request4k60(getWindow());
        session = new ParticleSession(this);
        setContentView(session.content);
    }

    @Override public void onResume() { super.onResume(); session.resume(); }
    @Override public void onPause() { session.pause(); super.onPause(); }
    @Override public void onDestroy() { session.close(); super.onDestroy(); }
}
