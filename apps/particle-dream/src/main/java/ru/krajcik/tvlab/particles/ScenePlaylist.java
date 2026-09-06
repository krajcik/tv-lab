package ru.krajcik.tvlab.particles;

final class ScenePlaylist {
    final SceneSource[] sources;
    final boolean[] text;
    final int[] modes;
    final int modeSeconds;
    final float[][] eagerTargets; // Only the small, dependency-free CPU/GPU test adapter.

    ScenePlaylist(SceneSource[] sources,boolean[] text){this(sources,text,new int[sources.length]);}
    ScenePlaylist(SceneSource[] sources,boolean[] text,int[] modes){this(sources,text,modes,3600);}
    ScenePlaylist(SceneSource[] sources,boolean[] text,int[] modes,int seconds){
        modeSeconds=ParticleModes.validDuration(seconds);
        if(sources.length==0||sources.length!=text.length||modes.length!=sources.length)throw new IllegalArgumentException("Scene metadata mismatch");
        this.sources=sources.clone();this.text=text.clone();this.modes=modes.clone();eagerTargets=null;
        for(int i=0;i<modes.length;i++){ParticleModes.validate(modes[i]);if(modes[i]>0&&text[i])throw new IllegalArgumentException("Mode cannot be text");}
    }
    ScenePlaylist(float[][] targets,boolean[] text){this(targets,text,new int[targets.length]);}
    ScenePlaylist(float[][] targets,boolean[] text,int[] modes){
        modeSeconds=3600;
        if(targets.length==0||targets.length!=text.length||modes.length!=targets.length)throw new IllegalArgumentException("Scene metadata mismatch");
        this.text=text.clone();this.modes=modes.clone();for(int mode:modes)ParticleModes.validate(mode);eagerTargets=targets;sources=new SceneSource[targets.length];
        java.util.IdentityHashMap<float[],SceneSource> recipes=new java.util.IdentityHashMap<>();
        for(int i=0;i<targets.length;i++){
            float[] cloud=targets[i];validate(cloud);
            sources[i]=recipes.computeIfAbsent(cloud,key->()->key);
        }
    }
    int size(){return sources.length;}
    static void validate(float[] cloud){
        if(cloud==null||cloud.length<4||cloud.length%4!=0)throw new IllegalArgumentException("Invalid target cloud");
        for(float value:cloud)if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite target");
    }
}
