package ru.krajcik.tvlab.particles;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One loader, three retained clouds. No decoding or waiting in peek/request. */
final class SceneCache implements AutoCloseable {
    private final ScenePlaylist playlist;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(task->{
        Thread thread=new Thread(task,"particle-scene-loader");thread.setDaemon(true);return thread;
    });
    private final Map<SceneSource,float[]> ready=new IdentityHashMap<>();
    private final Set<SceneSource> failed=Collections.newSetFromMap(new IdentityHashMap<>());
    private List<SceneSource> wanted=new ArrayList<>();
    private boolean closed,draining;
    private int initial=-1;

    SceneCache(ScenePlaylist playlist){this.playlist=playlist;}

    /** Called once on the session's background bootstrap thread, never the GL/UI thread. */
    void prepareInitial() throws Exception {
        Exception last=null;
        for(int i=0;i<playlist.size();i++){
            synchronized(this){if(closed)throw new CancellationException();}
            SceneSource source=playlist.sources[i];
            try{
                float[] cloud=source.load();validate(cloud);
                synchronized(this){
                    if(closed)throw new CancellationException();
                    ready.put(source,cloud);initial=i;
                }
                request(i);return;
            }catch(CancellationException error){throw error;}
            catch(Exception error){synchronized(this){if(closed)throw new CancellationException();failed.add(source);}last=error;}
        }
        throw new IllegalStateException("No usable scenes",last);
    }
    synchronized int initialIndex(){return initial;}
    synchronized float[] peek(int index){return ready.get(playlist.sources[index]);}
    synchronized boolean failed(int index){return failed.contains(playlist.sources[index]);}
    void request(int active){request(active,(active+1)%playlist.size());}
    synchronized void request(int active,int next){
        if(closed)return;
        List<SceneSource> window=new ArrayList<>(3);
        for(int index:new int[]{active,next,(next+1)%playlist.size()}){
            SceneSource source=playlist.sources[index];
            if(!contains(window,source))window.add(source);
        }
        wanted=window;ready.keySet().removeIf(source->!contains(wanted,source));
        if(!draining){
            for(SceneSource source:wanted)if(!ready.containsKey(source)&&!failed.contains(source)){draining=true;worker.execute(this::drain);break;}
        }
    }
    private void drain(){
        while(true){
            SceneSource source=null;
            synchronized(this){
                if(closed){draining=false;return;}
                for(SceneSource candidate:wanted)if(!ready.containsKey(candidate)&&!failed.contains(candidate)){source=candidate;break;}
                if(source==null){draining=false;return;}
            }
            float[] result=null;boolean bad=false;
            try{result=source.load();validate(result);}
            catch(Exception error){bad=true;}
            synchronized(this){
                if(closed){draining=false;return;}
                if(bad)failed.add(source);
                else if(contains(wanted,source))ready.put(source,result);
                notifyAll();
            }
        }
    }
    private static void validate(float[] cloud){ScenePlaylist.validate(cloud);if(cloud.length>262144)throw new IllegalArgumentException("Cloud exceeds 1 MiB cache entry limit");}
    private static boolean contains(List<SceneSource> list,SceneSource source){
        for(SceneSource item:list)if(item==source)return true;return false;
    }
    synchronized int residentCount(){return ready.size();}
    synchronized long residentBytes(){long bytes=0;for(float[] cloud:ready.values())bytes+=cloud.length*4L;return bytes;}
    @Override public synchronized void close(){
        closed=true;wanted.clear();ready.clear();failed.clear();worker.shutdownNow();notifyAll();
    }
}
