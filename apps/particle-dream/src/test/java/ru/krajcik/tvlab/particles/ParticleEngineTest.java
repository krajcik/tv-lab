package ru.krajcik.tvlab.particles;

public final class ParticleEngineTest {
    public static void main(String[] args) {
        float[][] targets={{0,0,1,1},{.2f,.1f,1,1}};
        ParticleEngine a=new ParticleEngine(256,targets,18.75f,0);
        ParticleEngine b=new ParticleEngine(256,targets,18.75f,0);
        for(int i=0;i<459;i++){a.step(1f/30);b.step(1f/30);}
        double distance=0,capture=0;int captured=0;
        for(int i=0;i<a.state.length;i++)require(a.state[i]==b.state[i],"Deterministic reference simulation");
        for(int i=0;i<a.count;i++){
            if(a.properties[i*6+3]>.5f)continue;
            distance+=Math.hypot(a.state[i*6],a.state[i*6+1]);capture+=a.state[i*6+4];captured++;
        }
        require(capture/captured>.6,"Individual capture must form the target near 15s, capture="+capture/captured);
        require(distance/captured<.13,"Particles must converge during the short reveal, distance="+distance/captured);
        for(int i=459;i<1800;i++){
            a.step(1f/30);
            for(int j=0;j<a.state.length;j++)require(Float.isFinite(a.state[j]),"Finite state through vortex expiry and collisions");
            for(int j=0;j<a.count;j++)require(Math.abs(a.state[j*6])<=FlowField.EXTENT_X&&Math.abs(a.state[j*6+1])<=FlowField.EXTENT_Y,"Solid boundaries");
        }
        require(a.sceneIndex()==0,"Playlist repeats after two scenes");
        ParticleEngine walls=new ParticleEngine(2,targets,18.75f,0);
        walls.state[0]=FlowField.EXTENT_X;walls.state[2]=1;
        walls.step(1f/60);
        require(walls.state[0]>.8f&&walls.state[2]<0&&walls.state[5]>0,"A wall must reflect and brighten, not wrap");
        ParticleEngine pause=new ParticleEngine(2,targets,18.75f,0);
        pause.step(Float.NaN);pause.step(Float.POSITIVE_INFINITY);pause.step(-1);pause.step(10000);
        require(pause.time<=.0451f,"A pause must not jump through scenes");
        System.out.println("PASS: deterministic flow, staggered capture near 15s, target convergence, 60-second lifecycle, solid rebound, resume clamp");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
