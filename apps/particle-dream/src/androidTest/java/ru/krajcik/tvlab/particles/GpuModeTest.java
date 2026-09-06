package ru.krajcik.tvlab.particles;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Actual GPU stability/containment at multiple story ages, plus full-density captures. */
final class GpuModeTest {
    static String run(Instrumentation test,Activity activity)throws Exception{
        StringBuilder report=new StringBuilder();
        for(int mode=1;mode<=8;mode++){
            final int id=mode;CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();GLSurfaceView[] view=new GLSurfaceView[1];
            ScenePlaylist playlist=SceneFactory.modePlaylist(mode);SceneCache cache=new SceneCache(playlist);cache.prepareInitial();
            ParticleEngine engine=new ParticleEngine(200000,playlist,cache,18.75f,0);
            test.runOnMainSync(()->{
                GLSurfaceView surface=new GLSurfaceView(activity);view[0]=surface;surface.setEGLContextClientVersion(3);surface.getHolder().setFixedSize(1920,1080);
                ParticleRenderer renderer=new ParticleRenderer(activity,engine);
                surface.setRenderer(new GLSurfaceView.Renderer(){boolean finished;
                    public void onSurfaceCreated(GL10 gl,EGLConfig config){try{renderer.onSurfaceCreated(gl,config);}catch(Throwable e){error.set(e);finished=true;done.countDown();}}
                    public void onSurfaceChanged(GL10 gl,int w,int h){if(!finished)renderer.onSurfaceChanged(gl,w,h);}
                    public void onDrawFrame(GL10 gl){if(finished)return;finished=true;try{
                        // Seek only the narrative clock. Particle dynamics still integrate with bounded dt.
                        Field clock=ParticleEngine.class.getDeclaredField("sceneElapsed");clock.setAccessible(true);
                        for(double age:new double[]{0,35,160,1600,10800,86400}){
                            clock.setDouble(engine,age);
                            for(int frame=0;frame<90;frame++)renderer.renderFrame(.045f);
                            validate(renderer.readState());
                        }
                        clock.setDouble(engine,id==5?145:40);
                        for(int frame=0;frame<650;frame++)renderer.renderFrame(.045f);
                        float[] state=renderer.readState();validate(state);
                        double x=0,y=0,r=0;for(int i=0;i<state.length;i+=6){x+=state[i];y+=state[i+1];r+=state[i]*state[i]+state[i+1]*state[i+1];}
                        report.append(id).append(" ").append(ParticleModes.NAMES[id-1]).append(": bounded/finite at 0..24h; rmsRadius=").append(Math.sqrt(r/engine.count)).append('\n');
                        ByteBuffer bytes=ByteBuffer.allocateDirect(1920*1080*4);GLES30.glReadPixels(0,0,1920,1080,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,bytes);
                        if(GLES30.glGetError()!=GLES30.GL_NO_ERROR)throw new AssertionError("GPU capture failed");
                        int[] pixels=new int[1920*1080];int bright=0;
                        for(int py=0;py<1080;py++)for(int px=0;px<1920;px++){int k=((1079-py)*1920+px)*4;int red=bytes.get(k)&255;pixels[py*1920+px]=0xff000000|(red<<16)|((bytes.get(k+1)&255)<<8)|(bytes.get(k+2)&255);if(red>24)bright++;}
                        if(bright<500)throw new AssertionError("Empty mode "+id);
                        Bitmap bitmap=Bitmap.createBitmap(pixels,1920,1080,Bitmap.Config.ARGB_8888);
                        try(FileOutputStream out=new FileOutputStream(new File(activity.getCacheDir(),"mode-"+id+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
                    }catch(Throwable e){error.set(e);}finally{done.countDown();}}
                });surface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);activity.setContentView(surface);
            });
            boolean completed=done.await(150,TimeUnit.SECONDS);test.runOnMainSync(()->view[0].onPause());cache.close();
            if(!completed)throw new AssertionError("Mode timeout "+id);if(error.get()!=null)throw new AssertionError("Mode "+id,error.get());
        }
        return "PASS: all eight GPU modes, 200000 particles, bounds/finite state, sampled story ages through24h (not realtime endurance)\n"+report;
    }
    private static void validate(float[] state){for(int i=0;i<state.length;i+=6){for(int j=0;j<6;j++)if(!Float.isFinite(state[i+j]))throw new AssertionError("Nonfinite state");if(Math.abs(state[i])>FlowField.EXTENT_X+.00001f||Math.abs(state[i+1])>FlowField.EXTENT_Y+.00001f)throw new AssertionError("Particle escaped");}}
}
