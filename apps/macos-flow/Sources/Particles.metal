#include <metal_stdlib>
using namespace metal;
struct State { float4 motion; float4 capture; };
struct Properties { float4 base; float4 offset; };
struct Vortex { float4 center; float4 shape; float4 seed; };
struct Uniforms { float4 clock; float4 formation; float4 geometry; float4 extra; float4 projection; };
// geometry: extents.xy, logical scale, pixel ratio; extra: flowTime, cycle, targets, particles.
kernel void buildField(device float2* field [[buffer(0)]], constant Vortex* v [[buffer(1)]], constant Uniforms& u [[buffer(2)]], uint id [[thread_position_in_grid]]) {
    if (id>=65*49) return;
    float2 extent=u.geometry.xy;

    float2 p=(float2(id%65,id/65)/float2(64.,48.)*2.-1.)*extent;
    float x=p.x,y=p.y,q=u.extra.x*.23;
    float2 f=float2(
      .055*sin(3.3*x-.43*q)*cos(4.1*y+.62*q)+.065*sin(8.3*x+q)*cos(10.7*y-.83*q)+.026*sin(19.1*x-1.63*q)*cos(23.7*y+1.31*q),
      -.055*(3.3/4.1)*cos(3.3*x-.43*q)*sin(4.1*y+.62*q)-.065*(8.3/10.7)*cos(8.3*x+q)*sin(10.7*y-.83*q)-.026*(19.1/23.7)*cos(19.1*x-1.63*q)*sin(23.7*y+1.31*q));
    for(int i=0;i<8;i++){
        float2 d=p-v[i].center.xy;float r2=dot(d,d),radius=v[i].shape.x,spin=v[i].shape.y,life=v[i].shape.z,shock=v[i].shape.w;
        float region=exp(-r2/(radius*radius*3.3)),r=sqrt(r2)+.001,angle=atan2(d.y,d.x),shell=r/radius;
        float layers=1.+.46*sin(shell*12.-angle*2.-q*1.2+v[i].seed.x)+.24*sin(shell*23.+angle*3.+q*.81);
        float swirl=spin*life*region*layers/(.014+r2);
        float shear=.042*life*region*sin(shell*17.-angle*3.+q*1.47),ring=(1.-shock)*radius*2.2;
        float force=shock*exp(-(r-ring)*(r-ring)/.013)*.32;
        f+=(float2(-d.y,d.x)*swirl+v[i].center.zw*region*.9)*.85+d/r*(force+shear);
    }
    field[id]=f;
}
kernel void meanField(device const float2* field [[buffer(0)]], device float2* mean [[buffer(1)]], uint id [[thread_position_in_grid]]) {
    float2 sum=0; for(uint i=0;i<65*49;i++) sum+=field[i]; mean[0]=sum*(.9/(65.*49.));
}
kernel void simulate(device const State* input [[buffer(0)]], device State* output [[buffer(1)]], device const Properties* props [[buffer(2)]], device const float4* targets [[buffer(3)]], device const float2* field [[buffer(4)]], device const float2* mean [[buffer(5)]], constant Uniforms& u [[buffer(6)]], uint id [[thread_position_in_grid]]) {
    if(id>=uint(u.extra.w)) return;
    State s=input[id]; float2 extent=u.geometry.xy;

    float dt=u.clock.x,t=u.clock.y,phase=u.clock.z,motion=u.clock.w;
    float2 p=s.motion.xy,velocity=s.motion.zw;
    float seed=props[id].base.x,capture=s.capture.xy.x,impact=s.capture.xy.y;
    float peak=u.formation.x,release=u.formation.y,end=u.formation.z;
    bool words=u.formation.w>.5;
    float local=phase-(props[id].base.z-.5)*min(.25,u.extra.y*.04);
    float likeness=smoothstep(0.,peak,local)*(1.-smoothstep(release,end,local));
    float settled=smoothstep(peak-.6,peak+.4,local)*(1.-smoothstep(release,release+.8,local));
    float desired=props[id].base.w>.5 ? 0. : min(.99,likeness*(words?1.02:1.0626*(.9+.1*sin(t*2.7+seed*9.))));
    if(props[id].base.w<.5)desired=mix(desired,min(.99,likeness*1.02),settled);
    capture+=(desired-capture)*(1.-exp(-dt*(desired<capture?7.:2.8)));
    if(capture<.00001)capture=0.;
    float2 grid=clamp((p+extent)/(2.*extent)*float2(64.,48.),float2(0.),float2(63.9999,47.9999));
    int2 cell=int2(floor(grid));float2 f=fract(grid);
    float2 forceField=mix(mix(field[cell.y*65+cell.x],field[cell.y*65+cell.x+1],f.x),
        mix(field[(cell.y+1)*65+cell.x],field[(cell.y+1)*65+cell.x+1],f.x),f.y);
    forceField-=mean[0];
    float clarity=settled*smoothstep(.65,.98,capture);
    float2 target=targets[id % uint(u.extra.z)].xy+props[id].offset.xy*mix(1.,words?.15:.35,clarity);
    float2 sway=float2(.023*sin(t*.29),-.02+.019*cos(t*.23));
    target+=sway+mix(words?.002:.011,words?.00015:.0006,clarity)*float2(sin(t*2.8+seed*31.+target.y*18.),cos(t*2.4+seed*27.+target.x*19.));
    float freedom=(.28+.72*(1.-capture)*(1.-capture))*(words?.36:1.)*(1.-.92*clarity);
    float pull=mix(words?90.:30.,words?160.:75.,clarity)*capture*capture*capture;
    float damping=mix(words?16.1:6.1,words?22.:14.,clarity)*capture,layer=.8+floor(seed*4.)*.15,response=1.8+fmod(seed,.25)*4.;
    velocity+=((forceField*layer*motion-velocity)*response*freedom+(target-p)*pull-velocity*damping)*dt;
    float speed=length(velocity);if(speed>2.5)velocity*=2.5/speed;
    p+=velocity*dt;impact*=exp(-dt*5.);
    if(abs(p.x)>extent.x){p.x=sign(p.x)*extent.x;impact=min(1.,abs(velocity.x)*1.4);velocity.x=-sign(p.x)*abs(velocity.x)*.83;velocity.y+=(seed-.5)*.18;}
    if(abs(p.y)>extent.y){p.y=sign(p.y)*extent.y;impact=max(impact,min(1.,abs(velocity.y)*1.4));velocity.y=-sign(p.y)*abs(velocity.y)*.83;velocity.x+=(seed-.5)*.18;}
    output[id].motion=float4(p,velocity);output[id].capture=float4(capture,impact,0,0);

}

struct Point { float4 position [[position]]; float size [[point_size]]; float alpha; };
vertex Point pointVertex(device const State* states [[buffer(0)]], device const Properties* props [[buffer(1)]], device const float4* targets [[buffer(2)]], constant Uniforms& u [[buffer(3)]], uint id [[vertex_id]]) {
    State s=states[id]; float4 properties=props[id].base, target=targets[id%uint(u.extra.z)];
    float capture=s.capture.x,impact=s.capture.y,seed=properties.x;
    float alpha=mix(.13+seed*.27,target.z*target.w*.89,capture)+impact*.22*(1.-capture);
    alpha=min(9.,floor(clamp(alpha,0.,1.)*10.))*.1;
    float radius=properties.y*clamp(u.geometry.z/650.,.72,1.35)*(1.+(1.-capture)*impact*.45);
    float diameter=2.*radius*u.geometry.w;
    Point p; p.size=max(1.,diameter); p.alpha=alpha*min(1.,diameter*diameter);
    p.position=float4(s.motion.xy*u.projection.xy,0,1); return p;
}
fragment float4 pointFragment(Point p [[stage_in]], float2 coord [[point_coord]]) {
    return float4(1,1,1,p.alpha*(1.-smoothstep(.36,.5,length(coord-float2(.5)))));
}
struct Trail { float4 position [[position]]; float alpha; };
vertex Trail trailVertex(device const State* states [[buffer(0)]], device const State* previous [[buffer(1)]], constant Uniforms& u [[buffer(2)]], uint id [[vertex_id]], uint instance [[instance_id]]) {
    State s=states[instance]; float2 delta=s.motion.xy-previous[instance].motion.xy;
    bool visible=s.capture.x<.65&&(dot(delta,delta)*u.geometry.z*u.geometry.z>1.8||s.capture.y>.15);
    float2 p=s.motion.xy-(id==0?delta*1.2:float2(0));
    Trail result; result.position=float4(p*u.projection.xy,0,1); result.alpha=visible?.075:0.; return result;
}
fragment float4 trailFragment(Trail p [[stage_in]]) { return float4(1,1,1,p.alpha); }
