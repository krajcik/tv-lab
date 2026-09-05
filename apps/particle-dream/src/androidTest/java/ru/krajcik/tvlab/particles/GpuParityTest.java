package ru.krajcik.tvlab.particles;

import android.app.Activity;
import android.app.Instrumentation;
import android.opengl.GLSurfaceView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Compares actual GLES transform-feedback output against the independent CPU integrator. */
final class GpuParityTest {
    static String run(Instrumentation instrumentation, Activity activity) throws Exception {
        CountDownLatch done=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        float[] maximum={0};
        GLSurfaceView[] view=new GLSurfaceView[1];
        instrumentation.runOnMainSync(()->{
            GLSurfaceView surface=new GLSurfaceView(activity);view[0]=surface;
            surface.setEGLContextClientVersion(3);surface.getHolder().setFixedSize(256,144);
            ParticleEngine gpu=new ParticleEngine(256,new float[][]{{0,0,1,1}},18.75f,0);
            ParticleEngine cpu=new ParticleEngine(256,new float[][]{{0,0,1,1}},18.75f,0);
            ParticleRenderer renderer=new ParticleRenderer(activity,gpu);
            surface.setRenderer(new GLSurfaceView.Renderer(){
                boolean finished;
                public void onSurfaceCreated(GL10 gl,EGLConfig config){
                    try{renderer.onSurfaceCreated(gl,config);}catch(Throwable error){failure.set(error);finished=true;done.countDown();}
                }
                public void onSurfaceChanged(GL10 gl,int width,int height){if(!finished)renderer.onSurfaceChanged(gl,width,height);}
                public void onDrawFrame(GL10 gl){
                    if(finished)return;finished=true;
                    try{
                        for(int frame=0;frame<340;frame++){renderer.renderFrame(.045f);cpu.step(.045f);}
                        float[] actual=renderer.readState();
                        for(int i=0;i<actual.length;i++){
                            if(!Float.isFinite(actual[i]))throw new AssertionError("GPU produced a non-finite particle");
                            maximum[0]=Math.max(maximum[0],Math.abs(actual[i]-cpu.state[i]));
                        }
                        if(maximum[0]>.002f)throw new AssertionError("GPU/CPU divergence="+maximum[0]);
                    }catch(Throwable error){failure.set(error);}finally{done.countDown();}
                }
            });
            surface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);activity.setContentView(surface);
        });
        boolean completed=done.await(45,TimeUnit.SECONDS);
        instrumentation.runOnMainSync(()->view[0].onPause());
        if(!completed)throw new AssertionError("GPU parity timed out");
        if(failure.get()!=null)throw new AssertionError("GPU parity failed",failure.get());
        return "GPU/CPU max error="+maximum[0]+" (256 particles, 340 steps including reveal)";
    }
}
