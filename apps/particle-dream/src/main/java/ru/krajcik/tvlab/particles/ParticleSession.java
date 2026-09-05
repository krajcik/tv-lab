package ru.krajcik.tvlab.particles;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Builds the large point clouds off the UI thread, including before the dream's first frame. */
final class ParticleSession {
    final FrameLayout content;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ParticleEngine pending;
    private ParticleView view;
    private boolean running;
    private volatile boolean closed;

    ParticleSession(Context context){
        this.context=context;content=new FrameLayout(context);content.setBackgroundColor(Color.BLACK);
        worker.execute(()->{
            DreamConfig config=new DreamConfig(context);
            float[][] scenes=SceneFactory.create(context,config);
            if(closed)return;
            ParticleEngine engine=new ParticleEngine(config.count,scenes,config.cycleSeconds,0);
            main.post(()->{if(!closed){pending=engine;attach();}});
        });
    }
    private void attach(){
        if(!running||closed||view!=null||pending==null)return;
        view=new ParticleView(context,pending);pending=null;
        content.addView(view,new FrameLayout.LayoutParams(-1,-1));view.onResume();
    }
    void resume(){running=true;if(view!=null)view.onResume();else attach();}
    void pause(){running=false;if(view!=null)view.onPause();}
    void close(){closed=true;pause();worker.shutdownNow();pending=null;content.removeAllViews();view=null;}
}
