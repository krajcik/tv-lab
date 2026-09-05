package ru.krajcik.tvlab.particles;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** 200k GPU particles, with no framebuffer upscaling or CPU position uploads per frame. */
final class ParticleRenderer implements GLSurfaceView.Renderer {
    private final Context context;
    private final ParticleEngine engine;
    private final FloatBuffer targetData;
    private final int[] states=new int[2], vaos=new int[2];
    private int properties, targets, fieldTexture, simulation, points, trails;
    private int clockUniform, cycleUniform, pointPixels, trailPixels, fieldUniform, meanUniform;
    private int fieldProgram, meanProgram, fieldBuffer, meanBuffer, meanTexture, emptyVao;
    private int centersUniform, shapesUniform, seedsUniform, flowTimeUniform, meanFieldUniform;
    private int current, targetScene=-1, width, height;
    private long lastFrame, reportStart, busyTotal, fieldTotal;
    private int frames, lateFrames;

    ParticleRenderer(Context context, ParticleEngine engine) {
        this.context=context;this.engine=engine;
        targetData=floats(engine.targets.length);
    }

    @Override public void onSurfaceCreated(GL10 unused,EGLConfig config) {
        engine.restartAfterContextLoss();
        current=0;targetScene=-1;lastFrame=0;reportStart=0;frames=lateFrames=0;busyTotal=fieldTotal=0;
        simulation=program(R.raw.simulation_vert,R.raw.flat_frag,new String[]{"nextMotion","nextCaptureImpact"});
        points=program(R.raw.points_vert,R.raw.points_frag,null);
        trails=program(R.raw.trails_vert,R.raw.flat_frag,null);
        clockUniform=GLES30.glGetUniformLocation(simulation,"uClock");
        cycleUniform=GLES30.glGetUniformLocation(simulation,"uCycle");
        fieldUniform=GLES30.glGetUniformLocation(simulation,"uField");
        meanUniform=GLES30.glGetUniformLocation(simulation,"uMean");
        fieldProgram=program(R.raw.field_vert,R.raw.flat_frag,new String[]{"nextField"});
        meanProgram=program(R.raw.mean_vert,R.raw.flat_frag,new String[]{"nextMean"});
        centersUniform=GLES30.glGetUniformLocation(fieldProgram,"uCenters[0]");
        shapesUniform=GLES30.glGetUniformLocation(fieldProgram,"uShapes[0]");
        seedsUniform=GLES30.glGetUniformLocation(fieldProgram,"uSeeds[0]");
        flowTimeUniform=GLES30.glGetUniformLocation(fieldProgram,"uFlowTime");
        meanFieldUniform=GLES30.glGetUniformLocation(meanProgram,"uField");
        pointPixels=GLES30.glGetUniformLocation(points,"uPixels");
        trailPixels=GLES30.glGetUniformLocation(trails,"uPixels");
        GLES30.glGenBuffers(2,states,0);
        FloatBuffer initial=floats(engine.state.length);initial.put(engine.state).position(0);
        for(int state:states){GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,state);GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,engine.state.length*4,initial,GLES30.GL_DYNAMIC_COPY);}
        properties=buffer(engine.properties,GLES30.GL_STATIC_DRAW);
        targets=buffer(engine.targets,GLES30.GL_DYNAMIC_DRAW);
        GLES30.glGenVertexArrays(2,vaos,0);
        for(int i=0;i<2;i++){
            GLES30.glBindVertexArray(vaos[i]);
            attribute(0,4,states[i],24,0);attribute(1,2,states[i],24,16);
            attribute(2,4,properties,24,0);attribute(3,2,properties,24,16);
            attribute(4,4,targets,16,0);attribute(5,2,states[1-i],24,0);
        }
        GLES30.glBindVertexArray(0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);
        int[] id=new int[1];
        GLES30.glGenVertexArrays(1,id,0);emptyVao=id[0];
        fieldBuffer=buffer(new float[65*49*2],GLES30.GL_DYNAMIC_COPY);
        meanBuffer=buffer(new float[2],GLES30.GL_DYNAMIC_COPY);
        GLES30.glGenTextures(1,id,0);meanTexture=id[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,meanTexture);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_NEAREST);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RG32F,1,1,0,GLES30.GL_RG,GLES30.GL_FLOAT,null);
        GLES30.glGenTextures(1,id,0);fieldTexture=id[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,fieldTexture);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RG32F,65,49,0,GLES30.GL_RG,GLES30.GL_FLOAT,null);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);GLES30.glClearColor(0,0,0,1);
        Log.i("ParticlesPerf","GPU="+GLES30.glGetString(GLES30.GL_RENDERER)+"; particles="+engine.count);
        checkError("initialization");
    }

    @Override public void onSurfaceChanged(GL10 unused,int width,int height){
        this.width=width;this.height=height;GLES30.glViewport(0,0,width,height);
        Log.i("ParticlesPerf","surface="+width+"x"+height+"; render="+width+"x"+height+"; target=60Hz; no intermediate framebuffer");
    }

    @Override public void onDrawFrame(GL10 unused){
        long now=System.nanoTime();
        float seconds=lastFrame==0?1f/60:(now-lastFrame)/1e9f;
        lastFrame=now;
        if(seconds>.025f)lateFrames++;
        renderFrame(seconds);
        busyTotal+=System.nanoTime()-now;frames++;
        if(reportStart==0)reportStart=now;
        if(now-reportStart>=5_000_000_000L){
            Log.i("ParticlesPerf",String.format(java.util.Locale.ROOT,
                "surface=%dx%d drawCallbacks=%.1f/s cpuSubmit=%.2fms field=%.2fms intervalsOver25ms=%d/%d",
                width,height,frames*1e9/(now-reportStart),busyTotal/1e6/frames,fieldTotal/1e6/frames,lateFrames,frames));
            reportStart=now;frames=lateFrames=0;busyTotal=fieldTotal=0;checkError("frame batch");
        }
    }

    void renderFrame(float seconds){
        if(width==0||height==0)return;
        long start=System.nanoTime();engine.advance(seconds);fieldTotal+=System.nanoTime()-start;
        buildFieldOnGpu();
        if(targetScene!=engine.sceneIndex()){
            targetData.clear();targetData.put(engine.targets).position(0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,targets);
            GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER,0,engine.targets.length*4,targetData);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);targetScene=engine.sceneIndex();
        }
        GLES30.glUseProgram(simulation);GLES30.glUniform1i(fieldUniform,0);GLES30.glUniform1i(meanUniform,1);
        GLES30.glUniform4f(clockUniform,engine.dt,engine.time,engine.phase,engine.motion);
        GLES30.glUniform1f(cycleUniform,engine.cycleSeconds());
        GLES30.glBindVertexArray(vaos[current]);
        // VAO attribute 5 references the previous state. Disable it during transform feedback.
        GLES30.glDisableVertexAttribArray(5);
        GLES30.glEnable(GLES30.GL_RASTERIZER_DISCARD);
        GLES30.glBindBufferBase(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER,0,states[1-current]);
        GLES30.glBeginTransformFeedback(GLES30.GL_POINTS);GLES30.glDrawArrays(GLES30.GL_POINTS,0,engine.count);GLES30.glEndTransformFeedback();
        GLES30.glBindBufferBase(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER,0,0);
        GLES30.glDisable(GLES30.GL_RASTERIZER_DISCARD);GLES30.glEnableVertexAttribArray(5);
        current=1-current;GLES30.glBindVertexArray(vaos[current]);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA);
        float ratio=Math.max(1,height/1080f),scale=height*.97f/ratio;
        GLES30.glUseProgram(trails);GLES30.glUniform2f(trailPixels,scale,ratio);
        GLES30.glLineWidth(Math.max(1,.55f*ratio));
        for(int a=0;a<6;a++)GLES30.glVertexAttribDivisor(a,1);
        GLES30.glDrawArraysInstanced(GLES30.GL_LINES,0,2,Math.min(10000,engine.count));
        for(int a=0;a<6;a++)GLES30.glVertexAttribDivisor(a,0);
        GLES30.glUseProgram(points);GLES30.glUniform2f(pointPixels,scale,ratio);
        GLES30.glDrawArrays(GLES30.GL_POINTS,0,engine.count);GLES30.glBindVertexArray(0);
    }

    private void buildFieldOnGpu(){
        GLES30.glBindVertexArray(emptyVao);GLES30.glEnable(GLES30.GL_RASTERIZER_DISCARD);
        GLES30.glUseProgram(fieldProgram);
        GLES30.glUniform4fv(centersUniform,8,engine.field.centers,0);
        GLES30.glUniform4fv(shapesUniform,8,engine.field.shapes,0);
        GLES30.glUniform4fv(seedsUniform,8,engine.field.seeds,0);
        GLES30.glUniform1f(flowTimeUniform,engine.flowTime);
        feedback(fieldBuffer,65*49);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        transferTexture(fieldTexture,fieldBuffer,65,49);
        GLES30.glUseProgram(meanProgram);GLES30.glUniform1i(meanFieldUniform,0);
        feedback(meanBuffer,1);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        transferTexture(meanTexture,meanBuffer,1,1);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glDisable(GLES30.GL_RASTERIZER_DISCARD);
    }
    private static void feedback(int destination,int count){
        GLES30.glBindBufferBase(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER,0,destination);
        GLES30.glBeginTransformFeedback(GLES30.GL_POINTS);GLES30.glDrawArrays(GLES30.GL_POINTS,0,count);GLES30.glEndTransformFeedback();
        GLES30.glBindBufferBase(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER,0,0);
    }
    private static void transferTexture(int texture,int source,int width,int height){
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,texture);
        GLES30.glBindBuffer(GLES30.GL_PIXEL_UNPACK_BUFFER,source);
        // With a PBO bound, a null pointer is byte offset zero, not host memory.
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D,0,0,0,width,height,GLES30.GL_RG,GLES30.GL_FLOAT,(java.nio.Buffer)null);
        GLES30.glBindBuffer(GLES30.GL_PIXEL_UNPACK_BUFFER,0);
    }

    /** Instrumentation only: synchronously read back a small parity-test simulation. */
    float[] readState(){
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,states[current]);
        ByteBuffer mapped=(ByteBuffer)GLES30.glMapBufferRange(GLES30.GL_ARRAY_BUFFER,0,engine.state.length*4,GLES30.GL_MAP_READ_BIT);
        if(mapped==null)throw new IllegalStateException("State readback failed");
        float[] result=new float[engine.state.length];mapped.order(ByteOrder.nativeOrder()).asFloatBuffer().get(result);
        GLES30.glUnmapBuffer(GLES30.GL_ARRAY_BUFFER);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);return result;
    }

    private static void attribute(int index,int size,int buffer,int stride,int offset){
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,buffer);GLES30.glEnableVertexAttribArray(index);
        GLES30.glVertexAttribPointer(index,size,GLES30.GL_FLOAT,false,stride,offset);
    }
    private static int buffer(float[] values,int usage){
        int[] id=new int[1];GLES30.glGenBuffers(1,id,0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,id[0]);
        FloatBuffer data=floats(values.length);data.put(values).position(0);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,values.length*4,data,usage);return id[0];
    }
    private static FloatBuffer floats(int count){return ByteBuffer.allocateDirect(count*4).order(ByteOrder.nativeOrder()).asFloatBuffer();}
    private int program(int vertex,int fragment,String[] varyings){
        int vs=shader(GLES30.GL_VERTEX_SHADER,read(vertex)),fs=shader(GLES30.GL_FRAGMENT_SHADER,read(fragment));
        int id=GLES30.glCreateProgram();GLES30.glAttachShader(id,vs);GLES30.glAttachShader(id,fs);
        if(varyings!=null)GLES30.glTransformFeedbackVaryings(id,varyings,GLES30.GL_INTERLEAVED_ATTRIBS);
        GLES30.glLinkProgram(id);int[] status=new int[1];GLES30.glGetProgramiv(id,GLES30.GL_LINK_STATUS,status,0);
        GLES30.glDeleteShader(vs);GLES30.glDeleteShader(fs);
        if(status[0]==0)throw new IllegalStateException(GLES30.glGetProgramInfoLog(id));return id;
    }
    private static int shader(int type,String source){
        int id=GLES30.glCreateShader(type);GLES30.glShaderSource(id,source);GLES30.glCompileShader(id);
        int[] status=new int[1];GLES30.glGetShaderiv(id,GLES30.GL_COMPILE_STATUS,status,0);
        if(status[0]==0)throw new IllegalStateException(GLES30.glGetShaderInfoLog(id));return id;
    }
    private String read(int resource){
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(context.getResources().openRawResource(resource),StandardCharsets.UTF_8))){
            StringBuilder result=new StringBuilder();String line;while((line=reader.readLine())!=null)result.append(line).append('\n');return result.toString();
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
    private static void checkError(String stage){int error=GLES30.glGetError();if(error!=GLES30.GL_NO_ERROR)throw new IllegalStateException(stage+" GL error="+error);}
}
