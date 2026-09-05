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
    private volatile SceneCache cache;

    ParticleSession(Context context){
        this.context=context;content=new FrameLayout(context);content.setBackgroundColor(Color.BLACK);
        worker.execute(()->{
            try {
                DreamConfig config=new DreamConfig(context);
                ScenePlaylist scenes=SceneFactory.playlist(context.getApplicationContext(),config);
                if(closed)return;
                SceneCache prepared=new SceneCache(scenes);cache=prepared;
                if(closed){prepared.close();return;}
                prepared.prepareInitial();
                if(closed){prepared.close();return;}
                ParticleEngine engine=new ParticleEngine(config.count,scenes,prepared,config.cycleSeconds,0);
                main.post(()->{if(!closed){pending=engine;attach();}});
            } catch(Exception error) {
                if(cache!=null)cache.close();
                if(!closed)main.post(()->{
                    if(closed)return;
                    android.util.Log.e("ParticleSession","Scene preparation failed",error);
                    android.widget.TextView message=new android.widget.TextView(context);
                    message.setText("Не удалось подготовить заставку. Попробуйте открыть настройки заново.");
                    message.setTextColor(Color.WHITE);message.setGravity(android.view.Gravity.CENTER);
                    content.addView(message,new FrameLayout.LayoutParams(-1,-1));
                });
            }
        });
    }
    private void attach(){
        if(!running||closed||view!=null||pending==null)return;
        view=new ParticleView(context,pending);pending=null;
        content.addView(view,new FrameLayout.LayoutParams(-1,-1));view.onResume();
    }
    void resume(){running=true;if(view!=null)view.onResume();else attach();}
    void pause(){running=false;if(view!=null)view.onPause();}
    void close(){closed=true;pause();worker.shutdownNow();if(cache!=null)cache.close();pending=null;content.removeAllViews();view=null;}
}
