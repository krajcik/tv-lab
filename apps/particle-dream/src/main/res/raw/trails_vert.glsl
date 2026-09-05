#version 300 es
precision highp float;
layout(location=0) in vec4 aMotion;
layout(location=1) in vec2 aCaptureImpact;
layout(location=5) in vec2 aPrevious;
uniform vec2 uPixels;
out float vAlpha;
void main(){
    vec2 delta=aMotion.xy-aPrevious;
    bool visible=aCaptureImpact.x<.65&&(dot(delta,delta)*uPixels.x*uPixels.x>1.8||aCaptureImpact.y>.15);
    vec2 p=aMotion.xy-(gl_VertexID==0?delta*1.2:vec2(0.));
    vAlpha=visible?.075:0.;
    gl_Position=vec4(p.x*(2.*.97/(16./9.)),-p.y*(2.*.97),0.,1.);
}
