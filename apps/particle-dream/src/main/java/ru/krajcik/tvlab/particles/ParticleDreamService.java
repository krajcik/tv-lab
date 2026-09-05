package ru.krajcik.tvlab.particles;

import android.service.dreams.DreamService;

public final class ParticleDreamService extends DreamService {
    private ParticleSession session;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        DisplaySetup.request4k60(getWindow());
        session = new ParticleSession(this);
        setContentView(session.content);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        if (session != null) session.resume();
    }

    @Override public void onDreamingStopped() {
        if (session != null) session.pause();
        super.onDreamingStopped();
    }

    @Override public void onDetachedFromWindow() {
        if (session != null) { session.close(); session = null; }
        super.onDetachedFromWindow();
    }
}
