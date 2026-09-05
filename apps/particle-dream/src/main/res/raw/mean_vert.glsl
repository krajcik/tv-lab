#version 300 es
precision highp float;
uniform sampler2D uField;
out vec2 nextMean;
out float vAlpha;
void main(){
    vec2 sum=vec2(0.);
    for(int y=0;y<49;y++)for(int x=0;x<65;x++)sum+=texelFetch(uField,ivec2(x,y),0).rg;
    nextMean=sum*(.9/(65.*49.));gl_Position=vec4(0.);vAlpha=0.;
}
