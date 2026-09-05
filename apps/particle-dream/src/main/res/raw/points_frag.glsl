#version 300 es
precision highp float;
in float vAlpha;
out vec4 color;
void main(){color=vec4(1.,1.,1.,vAlpha*(1.-smoothstep(.36,.5,length(gl_PointCoord-vec2(.5)))));}
