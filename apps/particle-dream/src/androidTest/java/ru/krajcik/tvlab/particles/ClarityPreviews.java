package ru.krajcik.tvlab.particles;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Deterministic screenshots of actual GPU formations; disposable emulator only. */
final class ClarityPreviews {
    static String write(Instrumentation instrumentation,Activity activity) throws Exception {
        StringBuilder report=new StringBuilder();
        java.util.List<QuoteLibrary.Quote> quotes=QuoteLibrary.read(activity);
        QuoteLibrary.Quote longest=quotes.stream().max(java.util.Comparator.comparingInt(q->q.text.length())).orElseThrow();
        for(String name:new String[]{"image","text","long-text"}){
            boolean words=!name.equals("image");QuoteLibrary.Quote quote=name.equals("long-text")?longest:quotes.get(0);
            float[] cloud=words?SceneFactory.text(quote.text,quote.author,"serif"):SceneFactory.image(activity,ImageLibrary.read(activity).get(0));
            ParticleEngine engine=new ParticleEngine(200000,new ScenePlaylist(new float[][]{cloud},new boolean[]{words}),18.75f,0);
            CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
            GLSurfaceView[] view=new GLSurfaceView[1];
            instrumentation.runOnMainSync(()->{
                GLSurfaceView surface=new GLSurfaceView(activity);view[0]=surface;surface.setEGLContextClientVersion(3);surface.getHolder().setFixedSize(3840,2160);
                ParticleRenderer renderer=new ParticleRenderer(activity,engine);
                surface.setRenderer(new GLSurfaceView.Renderer(){
                    boolean finished;
                    public void onSurfaceCreated(GL10 gl,EGLConfig config){try{renderer.onSurfaceCreated(gl,config);}catch(Throwable e){failure.set(e);finished=true;done.countDown();}}
                    public void onSurfaceChanged(GL10 gl,int w,int h){if(!finished)renderer.onSurfaceChanged(gl,w,h);}
                    public void onDrawFrame(GL10 gl){
                        if(finished)return;finished=true;
                        try{
                            for(int i=0;i<400;i++)renderer.renderFrame(.045f);
                            ByteBuffer pixels=ByteBuffer.allocateDirect(3840*2160*4).order(ByteOrder.nativeOrder());
                            GLES30.glReadPixels(0,0,3840,2160,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixels);
                            int error=GLES30.glGetError();if(error!=GLES30.GL_NO_ERROR)throw new AssertionError("Capture GL error="+error);
                            int[] argb=new int[3840*2160];
                            for(int y=0;y<2160;y++)for(int x=0;x<3840;x++){
                                int i=((2159-y)*3840+x)*4;
                                argb[y*3840+x]=0xff000000|((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255);
                            }
                            Bitmap bitmap=Bitmap.createBitmap(argb,3840,2160,Bitmap.Config.ARGB_8888);
                            try(FileOutputStream out=new FileOutputStream(new File(activity.getCacheDir(),"clarity-"+name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
                            float[] state=renderer.readState();double squared=0;int count=0;
                            float sx=.023f*(float)Math.sin(engine.time*.29f),sy=-.02f+.019f*(float)Math.cos(engine.time*.23f);
                            for(int i=0;i<engine.count;i++)if(state[i*6+4]>.9f){
                                double dx=state[i*6]-engine.targets[i*4]-sx,dy=state[i*6+1]-engine.targets[i*4+1]-sy;squared+=dx*dx+dy*dy;count++;
                            }
                            report.append(name).append(": cloud=").append(cloud.length/4).append(", held=").append(count).append(", targetRms=").append(Math.sqrt(squared/Math.max(1,count))).append('\n');
                        }catch(Throwable e){failure.set(e);}finally{done.countDown();}
                    }
                });surface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);activity.setContentView(surface);
            });
            boolean complete=done.await(120,TimeUnit.SECONDS);instrumentation.runOnMainSync(()->view[0].onPause());
            if(!complete)throw new AssertionError("Clarity capture timeout");if(failure.get()!=null)throw new AssertionError("Clarity capture failed",failure.get());
        }
        return report.toString();
    }
}
