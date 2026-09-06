#version 300 es
precision highp float;
layout(location=0) in vec4 aMotion;
layout(location=1) in vec2 aCaptureImpact;
layout(location=2) in vec4 aProperties;
layout(location=3) in vec2 aOffset;
layout(location=4) in vec4 aTarget;
uniform sampler2D uField;
uniform sampler2D uMean;
uniform vec4 uClock; // dt, time, phase, motion
uniform float uCycle;
uniform vec4 uFormation; // build, release start, release end, text flag
out vec4 nextMotion;
out vec2 nextCaptureImpact;
out float vAlpha;
const vec2 extent=vec2((16.0/9.0)/(2.0*.97)-.006,1.0/(2.0*.97)-.006);
void main(){
    float dt=uClock.x,t=uClock.y,phase=uClock.z,motion=uClock.w;
    vec2 p=aMotion.xy,velocity=aMotion.zw;
    float seed=aProperties.x,capture=aCaptureImpact.x,impact=aCaptureImpact.y;
    float peak=uFormation.x,release=uFormation.y,end=uFormation.z;
    bool words=uFormation.w>.5;
    float local=phase-(aProperties.z-.5)*min(.25,uCycle*.04);
    float likeness=smoothstep(0.,peak,local)*(1.-smoothstep(release,end,local));
    float settled=smoothstep(peak-.6,peak+.4,local)*(1.-smoothstep(release,release+.8,local));
    float desired=aProperties.w>.5 ? 0. : min(.99,likeness*(words?1.02:1.0626*(.9+.1*sin(t*2.7+seed*9.))));
    if(aProperties.w<.5)desired=mix(desired,min(.99,likeness*1.02),settled);
    capture+=(desired-capture)*(1.-exp(-dt*(desired<capture?7.:2.8)));
    if(capture<.00001)capture=0.;
    vec2 grid=clamp((p+extent)/(2.*extent)*vec2(64.,48.),vec2(0.),vec2(63.9999,47.9999));
    ivec2 cell=ivec2(floor(grid));vec2 f=fract(grid);
    vec2 field=mix(mix(texelFetch(uField,cell,0).rg,texelFetch(uField,cell+ivec2(1,0),0).rg,f.x),
        mix(texelFetch(uField,cell+ivec2(0,1),0).rg,texelFetch(uField,cell+ivec2(1,1),0).rg,f.x),f.y);
    field-=texelFetch(uMean,ivec2(0),0).rg;
    float clarity=settled*smoothstep(.65,.98,capture);
    vec2 target=aTarget.xy+aOffset*mix(1.,words?.15:.35,clarity);
    vec2 sway=vec2(.023*sin(t*.29),-.02+.019*cos(t*.23));
    target+=sway+mix(words?.002:.011,words?.00015:.0006,clarity)*vec2(sin(t*2.8+seed*31.+target.y*18.),cos(t*2.4+seed*27.+target.x*19.));
    float freedom=(.28+.72*(1.-capture)*(1.-capture))*(words?.36:1.)*(1.-.92*clarity);
    float pull=mix(words?90.:30.,words?160.:75.,clarity)*capture*capture*capture;
    float damping=mix(words?16.1:6.1,words?22.:14.,clarity)*capture,layer=.8+floor(seed*4.)*.15,response=1.8+mod(seed,.25)*4.;
    velocity+=((field*layer*motion-velocity)*response*freedom+(target-p)*pull-velocity*damping)*dt;
    float speed=length(velocity);if(speed>2.5)velocity*=2.5/speed;
    p+=velocity*dt;impact*=exp(-dt*5.);
    if(abs(p.x)>extent.x){p.x=sign(p.x)*extent.x;impact=min(1.,abs(velocity.x)*1.4);velocity.x=-sign(p.x)*abs(velocity.x)*.83;velocity.y+=(seed-.5)*.18;}
    if(abs(p.y)>extent.y){p.y=sign(p.y)*extent.y;impact=max(impact,min(1.,abs(velocity.y)*1.4));velocity.y=-sign(p.y)*abs(velocity.y)*.83;velocity.x+=(seed-.5)*.18;}
    nextMotion=vec4(p,velocity);nextCaptureImpact=vec2(capture,impact);
    gl_Position=vec4(0.);vAlpha=0.;
}
