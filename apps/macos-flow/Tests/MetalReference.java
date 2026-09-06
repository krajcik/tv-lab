package ru.krajcik.tvlab.particles;
import java.io.*;
/** Export the existing Android CPU reference without duplicating the integrator. */
public final class MetalReference {
    static void array(PrintWriter out,float[] values){out.print('[');for(int i=0;i<values.length;i++){if(i>0)out.print(',');out.print(values[i]);}out.print(']');}
    public static void main(String[] args)throws Exception{
        int count=256;
        float[] target=new float[count*4];for(int i=0;i<count;i++){target[i*4]=(FlowField.random(i+9)-.5f)*.8f;target[i*4+1]=(FlowField.random(i+31)-.5f)*.5f;target[i*4+2]=target[i*4+3]=1;}
        try(PrintWriter out=new PrintWriter(args[0],"UTF-8")){
            out.print("{\"targets\":");array(out,target);out.print(",\"cases\":[");
            for(int kind=0;kind<2;kind++){
                if(kind>0)out.print(',');
                ParticleEngine engine=new ParticleEngine(count,new ScenePlaylist(new float[][]{target},new boolean[]{kind==1}),18.75f,0);
                out.print("{\"properties\":");array(out,engine.properties);out.print(",\"blocks\":[");
                // Sample one GPU step at every second across full build/hold/release/rest.
                // Long chaotic trajectories amplify CPU-double/GPU-float rounding.
                for(int block=0;block<60;block++){
                    for(int warmup=0;warmup<59;warmup++)engine.step(1/60f);
                    if(block>0)out.print(',');out.print("{\"initial\":");array(out,engine.state);out.print(",\"steps\":[");
                    for(int step=0;step<1;step++){
                        engine.step(1/60f);if(step>0)out.print(',');
                        out.print("{\"clock\":");array(out,new float[]{engine.dt,engine.time,engine.phase,engine.motion});
                        out.print(",\"formation\":");array(out,new float[]{engine.timeline.build,engine.timeline.releaseStart,engine.timeline.releaseEnd,kind});
                        out.print(",\"extra\":");array(out,new float[]{engine.flowTime,engine.timeline.duration,count,count});
                        out.print(",\"centers\":");array(out,engine.field.centers);out.print(",\"shapes\":");array(out,engine.field.shapes);out.print(",\"seeds\":");array(out,engine.field.seeds);out.print('}');
                    }
                    out.print("],\"expected\":");array(out,engine.state);out.print('}');
                }
                out.print("]}");
            }
            out.print("]}");
        }
    }
}
