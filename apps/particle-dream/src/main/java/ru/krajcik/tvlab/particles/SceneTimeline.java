package ru.krajcik.tvlab.particles;

/** Independent scene durations: a readable text hold must never be truncated by the image cycle. */
final class SceneTimeline {
    final boolean[] text;
    private final double[] starts, durations;
    private final double total;
    private double firstStart;
    private int origin;
    int index;
    float phase, build, releaseStart, releaseEnd, duration;

    SceneTimeline(boolean[] text,float baseCycle){
        if(text.length==0)throw new IllegalArgumentException("Empty playlist");
        this.text=text.clone();starts=new double[text.length];durations=new double[text.length];
        double rest=Math.max(0,baseCycle-5.85f),sum=0;
        for(int i=0;i<text.length;i++){
            starts[i]=sum;
            durations[i]=(text[i]?8+20+5:6+3.9+3)+rest;
            sum+=durations[i];
        }
        total=sum;restartAt(0);
    }
    void restartAt(int index){origin=index;firstStart=15-(text[index]?8:6);update(0);}
    void update(double elapsed){
        double local=elapsed-firstStart;
        if(local<0){index=origin;phase=(float)local;}
        else {
            local=(local+starts[origin])%total;
            int low=0,high=starts.length-1;
            while(low<=high){int middle=(low+high)>>>1;if(starts[middle]<=local)low=middle+1;else high=middle-1;}
            index=Math.max(0,high);phase=(float)(local-starts[index]);
        }
        boolean words=text[index];build=words?8:6;
        releaseStart=build+(words?20:3.9f);releaseEnd=releaseStart+(words?5:3);
        duration=(float)durations[index];
    }
}
