#version 300 es
precision highp float;
uniform vec4 uCenters[8];
uniform vec4 uShapes[8];
uniform vec4 uSeeds[8];
uniform float uFlowTime;
uniform vec4 uMode;
uniform vec4 uWalls[4];
float wallWave(float distance,float age){float front=age*.16;return exp(-pow((distance-front)/.045,2.))*exp(-age*.24)*.32;}
out vec2 nextField;
out float vAlpha;
const vec2 extent=vec2((16.0/9.0)/(2.0*.97)-.006,1.0/(2.0*.97)-.006);
void main(){
    vec2 p=(vec2(gl_VertexID%65,gl_VertexID/65)/vec2(64.,48.)*2.-1.)*extent;
    float x=p.x,y=p.y,q=uFlowTime*.23;
    vec2 f=vec2(
      .055*sin(3.3*x-.43*q)*cos(4.1*y+.62*q)+.065*sin(8.3*x+q)*cos(10.7*y-.83*q)+.026*sin(19.1*x-1.63*q)*cos(23.7*y+1.31*q),
      -.055*(3.3/4.1)*cos(3.3*x-.43*q)*sin(4.1*y+.62*q)-.065*(8.3/10.7)*cos(8.3*x+q)*sin(10.7*y-.83*q)-.026*(19.1/23.7)*cos(19.1*x-1.63*q)*sin(23.7*y+1.31*q));
    for(int i=0;i<8;i++){
        vec2 d=p-uCenters[i].xy;float r2=dot(d,d),radius=uShapes[i].x,spin=uShapes[i].y,life=uShapes[i].z,shock=uShapes[i].w;
        float region=exp(-r2/(radius*radius*3.3)),r=sqrt(r2)+.001,angle=atan(d.y,d.x),shell=r/radius;
        float layers=1.+.46*sin(shell*12.-angle*2.-q*1.2+uSeeds[i].x)+.24*sin(shell*23.+angle*3.+q*.81);
        float swirl=spin*life*region*layers/(.014+r2);
        float shear=.042*life*region*sin(shell*17.-angle*3.+q*1.47),ring=(1.-shock)*radius*2.2;
        float force=shock*exp(-(r-ring)*(r-ring)/.013)*.32;
        f+=(vec2(-d.y,d.x)*swirl+uCenters[i].zw*region*.9)*.85+d/r*(force+shear);
    }
    if(uMode.x>2.5&&uMode.x<3.5){
        vec2 echoes=vec2(0);
        for(int i=0;i<4;i++){
            float age=uFlowTime-uWalls[i].z;
            if(uWalls[i].w<.01||age<0.||age>12.)continue;
            for(int mirror=0;mirror<5;mirror++){
                vec2 origin=uWalls[i].xy;
                if(mirror==1)origin.x=2.*extent.x-origin.x;
                if(mirror==2)origin.x=-2.*extent.x-origin.x;
                if(mirror==3)origin.y=2.*extent.y-origin.y;
                if(mirror==4)origin.y=-2.*extent.y-origin.y;
                vec2 delta=p-origin;float distance=length(delta);
                echoes+=delta/max(.001,distance)*wallWave(distance,age)*uWalls[i].w;
            }
        }
        f=f*.4+echoes*uMode.w*4.;
    }
    nextField=f;gl_Position=vec4(0.);vAlpha=0.;
}
