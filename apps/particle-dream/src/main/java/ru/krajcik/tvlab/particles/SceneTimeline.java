package ru.krajcik.tvlab.particles;

/** Independent scene durations: a readable text hold must never be truncated by the image cycle. */
final class SceneTimeline {
    final boolean[] text;
    final int[] modes;
    private final double[] starts, durations;
    private final double total;
    private double firstStart;
    private int origin;
    int index;
    float phase, build, releaseStart, releaseEnd, duration;

    SceneTimeline(boolean[] text,float baseCycle){this(text,new int[text.length],baseCycle);}
    SceneTimeline(boolean[] text,int[] modes,float baseCycle){this(text,modes,baseCycle,3600);}
    SceneTimeline(boolean[] text,int[] modes,float baseCycle,int modeSeconds){
        if(text.length==0||text.length!=modes.length)throw new IllegalArgumentException("Empty playlist");
        this.text=text.clone();this.modes=modes.clone();for(int mode:modes)ParticleModes.validate(mode);starts=new double[text.length];durations=new double[text.length];
        double rest=Math.max(0,baseCycle-5.85f),sum=0;
        for(int i=0;i<text.length;i++){
            starts[i]=sum;
            durations[i]=modes[i]>0?(modeSeconds==0?Double.POSITIVE_INFINITY:modeSeconds):(text[i]?8+20+5:6+3.9+3)+rest;
            sum+=durations[i];
        }
        if(Double.isInfinite(sum)&&text.length!=1)throw new IllegalArgumentException("Endless mode requires one scene");
        total=sum;restartAt(0);
    }
    void restartAt(int index){origin=index;firstStart=modes[index]>0?0:15-(text[index]?8:6);update(0);}
    void update(double elapsed){
        double local=elapsed-firstStart;
        if(local<0){index=origin;phase=(float)local;}
        else {
            local=(local+starts[origin])%total;
            int low=0,high=starts.length-1;
            while(low<=high){int middle=(low+high)>>>1;if(starts[middle]<=local)low=middle+1;else high=middle-1;}
            index=Math.max(0,high);phase=(float)(local-starts[index]);
        }
        if(modes[index]>0){duration=(float)durations[index];build=8;releaseStart=duration-8;releaseEnd=duration;return;}
        boolean words=text[index];build=words?8:6;
        releaseStart=build+(words?20:3.9f);releaseEnd=releaseStart+(words?5:3);
        duration=(float)durations[index];
    }
}
