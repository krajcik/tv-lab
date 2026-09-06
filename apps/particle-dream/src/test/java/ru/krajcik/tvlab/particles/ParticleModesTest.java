package ru.krajcik.tvlab.particles;
final class ParticleModesTest {
    public static void main(String[] args){
        int[] modes={1,2,3,4,5,6,7,8};SceneTimeline hour=new SceneTimeline(new boolean[8],modes,18.75f,3600);
        for(int i=0;i<8;i++){hour.update(i*3600+1799);require(hour.index==i&&hour.duration==3600,"Hour duration");require(hour.modes[hour.index]==i+1,"Mode order");}
        hour.update(8*3600+10);require(hour.index==0,"Full cycle wraps");
        SceneTimeline endless=new SceneTimeline(new boolean[]{false},new int[]{8},18.75f,0);
        for(double time:new double[]{0,3599,10801,86400,604800}){endless.update(time);require(endless.index==0&&Float.isFinite(endless.phase),"Endless scene must not wrap or produce NaN");require(Float.isFinite(ParticleModes.blend(endless.phase,endless.duration)),"Finite forever envelope");}
        require(ParticleModes.blend(3600,Float.POSITIVE_INFINITY)==1,"No fadeout in endless scene");
        for(int seconds:ParticleModes.DURATIONS)require(ParticleModes.validDuration(seconds)==seconds,"Duration preserved");
        require(ParticleModes.validDuration(-1)==3600,"Invalid preference fallback");
        System.out.println("PASS: eight hourly scenes, wrap, endless 7-day timeline, finite fades, duration validation");
    }
    static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
}
