package ru.krajcik.tvlab.particles;

import android.service.dreams.DreamService;

public final class ParticleDreamService extends DreamService {
    private ParticleView view;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        view = new ParticleView(this);
        setContentView(view);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        if (view != null) view.onResume();
    }

    @Override public void onDreamingStopped() {
        if (view != null) view.onPause();
        super.onDreamingStopped();
    }

    @Override public void onDetachedFromWindow() {
        if (view != null) { view.onPause(); view = null; }
        super.onDetachedFromWindow();
    }
}
