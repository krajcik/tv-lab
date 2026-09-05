package ru.krajcik.tvlab.particles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public final class SceneCacheTest {
    public static void main(String[] args) throws Exception {
        largeCatalogue();nonblockingMiss();staleAndCancelled();failedScenes();
        System.out.println("PASS: lazy 100-image/1000-quote catalogue, bounded cache, shared recipes, nonblocking misses, cancellation, failures, context restart");
    }
    private static void largeCatalogue() throws Exception {
        AtomicInteger loads=new AtomicInteger();
        SceneSource[] photos=new SceneSource[100],sources=new SceneSource[2000];
        boolean[] text=new boolean[2000];
        for(int i=0;i<100;i++){final int id=i;photos[i]=()->cloud(262144,id,loads);}
        for(int i=0;i<1000;i++){final int id=i;sources[i*2]=photos[i%100];sources[i*2+1]=()->cloud(65536,id,loads);text[i*2+1]=true;}
        ScenePlaylist playlist=new ScenePlaylist(sources,text);
        require(loads.get()==0,"Creating catalogue must not materialize a cloud");
        long highWater=0;
        try(SceneCache cache=new SceneCache(playlist)){
            cache.prepareInitial();await(()->cache.peek(2)!=null);
            require(loads.get()<=3,"Startup must only prepare the first window");
            for(int i=0;i<2000;i++){
                int active=i,last=(i+2)%2000;
                cache.request(active);await(()->cache.peek(active)!=null&&cache.peek(last)!=null);
                require(cache.residentCount()<=3,"Cache must retain at most three unique clouds");
                highWater=Math.max(highWater,cache.residentBytes());
                require(cache.residentBytes()<=3L*1024*1024,"Cache must remain below three MiB");
            }
            require(playlist.sources[0]==playlist.sources[200],"Repeated images must share recipes");
        }
        System.out.println("Catalogue=1100 sources/2000 playlist entries; peak retained clouds="+highWater+" bytes; materializations="+loads.get());
    }
    private static float[] cloud(int floats,int id,AtomicInteger loads){
        loads.incrementAndGet();float[] result=new float[floats];result[0]=id*.0001f;result[2]=result[3]=1;return result;
    }
    private static void nonblockingMiss() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        SceneSource first=()->new float[]{0,0,1,1};
        SceneSource delayed=()->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Test gate timeout");return new float[]{.3f,0,1,1};};
        ScenePlaylist playlist=new ScenePlaylist(new SceneSource[]{first,delayed},new boolean[]{false,true});
        try(SceneCache cache=new SceneCache(playlist)){
            cache.prepareInitial();require(entered.await(1,TimeUnit.SECONDS),"Loader should prefetch");
            ParticleEngine engine=new ParticleEngine(4,playlist,cache,18.75f,0);
            long start=System.nanoTime();for(int i=0;i<900;i++)engine.advance(.045f);
            require((System.nanoTime()-start)<1_000_000_000L,"GL advance must not wait for a loader");
            require(engine.sceneIndex()==0&&engine.timeline.index==0,"Old targets and metadata must stay aligned on a miss");
            require(engine.time>40,"Free flow clock must keep advancing while scene clock waits");
            require(cache.peek(0)!=null,"Active scene must remain pinned");
            release.countDown();await(()->cache.peek(1)!=null);engine.advance(.016f);
            require(engine.sceneIndex()==1&&engine.timeline.text[1],"Ready text must publish atomically");
            require(engine.phase<.1f,"Waiting must not consume the next scene's reading time");
            engine.restartAfterContextLoss();
            require(engine.sceneIndex()==1&&engine.timeline.index==1&&engine.timeline.phase<0,"Context loss restarts the retained current scene without decoding");
        }finally{release.countDown();}
    }
    private static void staleAndCancelled() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        SceneSource slow=()->{entered.countDown();release.await();return new float[]{.1f,0,1,1};};
        SceneSource[] sources={()->new float[]{0,0,1,1},slow,()->new float[]{.2f,0,1,1},()->new float[]{.3f,0,1,1},()->new float[]{.4f,0,1,1}};
        try(SceneCache cache=new SceneCache(new ScenePlaylist(sources,new boolean[5]))){
            cache.prepareInitial();require(entered.await(1,TimeUnit.SECONDS),"Prefetch should start");
            cache.request(0,3);release.countDown();await(()->cache.peek(4)!=null);
            require(cache.peek(1)==null&&cache.residentCount()<=3,"Stale completion must not repopulate an evicted scene");
        }finally{release.countDown();}
        CountDownLatch loading=new CountDownLatch(1),cancelled=new CountDownLatch(1);
        SceneSource cancellable=()->{loading.countDown();try{new CountDownLatch(1).await();}catch(InterruptedException e){cancelled.countDown();throw e;}return null;};
        SceneCache cache=new SceneCache(new ScenePlaylist(new SceneSource[]{sources[0],cancellable},new boolean[2]));
        cache.prepareInitial();require(loading.await(1,TimeUnit.SECONDS),"Cancellation fixture started");cache.close();
        require(cancelled.await(1,TimeUnit.SECONDS),"Close must interrupt preparation");
        require(cache.residentCount()==0,"No cloud may remain after close");cache.request(0);require(cache.peek(0)==null,"Closed cache must not restart");
    }
    private static void failedScenes() throws Exception {
        SceneSource bad=()->{throw new java.io.IOException("Broken fixture");};
        SceneSource good=()->new float[]{.2f,0,1,1};
        ScenePlaylist playlist=new ScenePlaylist(new SceneSource[]{good,bad,good},new boolean[]{false,false,true});
        try(SceneCache cache=new SceneCache(playlist)){
            cache.prepareInitial();await(()->cache.failed(1));
            ParticleEngine engine=new ParticleEngine(4,playlist,cache,18.75f,0);
            for(int i=0;i<900;i++)engine.advance(.045f);
            require(engine.sceneIndex()==2,"A failed future scene must be skipped without stalling");
        }
        try(SceneCache cache=new SceneCache(new ScenePlaylist(new SceneSource[]{bad,good},new boolean[]{false,true}))){
            cache.prepareInitial();require(cache.initialIndex()==1,"A broken first scene must not prevent startup");
        }
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+2_000_000_000L;
        while(!condition.getAsBoolean()){if(System.nanoTime()>deadline)throw new AssertionError("Cache preparation timed out");Thread.sleep(1);}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
