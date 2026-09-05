package ru.krajcik.tvlab.particles;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

final class ParticleView extends GLSurfaceView {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!running) return;
            requestRender();
            handler.postAtTime(this, SystemClock.uptimeMillis() + 33);
        }
    };

    ParticleView(Context context) {
        super(context);
        DreamConfig config = new DreamConfig(context);
        ParticleEngine engine = new ParticleEngine(config.count, SceneFactory.create(context, config),
                config.cycleSeconds, System.nanoTime());
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(false);
        setRenderer(new ParticleRenderer(engine));
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    @Override public void onResume() {
        if (running) return;
        super.onResume();
        running = true;
        handler.post(frame);
    }

    @Override public void onPause() {
        running = false;
        handler.removeCallbacks(frame);
        super.onPause();
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        handler.removeCallbacks(frame);
        super.onDetachedFromWindow();
    }
}
