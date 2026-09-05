package ru.krajcik.tvlab.particles;

final class ScenePlaylist {
    final SceneSource[] sources;
    final boolean[] text;
    final float[][] eagerTargets; // Only the small, dependency-free CPU/GPU test adapter.

    ScenePlaylist(SceneSource[] sources,boolean[] text){
        if(sources.length==0||sources.length!=text.length)throw new IllegalArgumentException("Scene metadata mismatch");
        this.sources=sources.clone();this.text=text.clone();eagerTargets=null;
    }
    ScenePlaylist(float[][] targets,boolean[] text){
        if(targets.length==0||targets.length!=text.length)throw new IllegalArgumentException("Scene metadata mismatch");
        this.text=text.clone();eagerTargets=targets;sources=new SceneSource[targets.length];
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
