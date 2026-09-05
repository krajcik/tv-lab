package ru.krajcik.tvlab.particles;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.view.Surface;
import android.view.SurfaceHolder;

final class ParticleView extends GLSurfaceView {
    ParticleView(Context context, ParticleEngine engine) {
        super(context);
        setEGLContextClientVersion(3);
        setPreserveEGLContextOnPause(true);
        setRenderer(new ParticleRenderer(context,engine));
        // EGL swaps are paced by the display. There is no Handler timer or 720p render target.
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        getHolder().setFixedSize(3840,2160);
    }

    @Override public void surfaceCreated(SurfaceHolder holder){
        super.surfaceCreated(holder);
        if(Build.VERSION.SDK_INT>=31)holder.getSurface().setFrameRate(60,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
        else if(Build.VERSION.SDK_INT>=30)holder.getSurface().setFrameRate(60,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
    }
}
