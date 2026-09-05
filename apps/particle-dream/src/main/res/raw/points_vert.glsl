#version 300 es
precision highp float;
layout(location=0) in vec4 aMotion;
layout(location=1) in vec2 aCaptureImpact;
layout(location=2) in vec4 aProperties;
layout(location=4) in vec4 aTarget;
uniform vec2 uPixels; // logical scale, pixel ratio
out float vAlpha;
void main(){
    float capture=aCaptureImpact.x,impact=aCaptureImpact.y,seed=aProperties.x;
    float alpha=mix(.13+seed*.27,aTarget.z*aTarget.w*.89,capture)+impact*.22*(1.-capture);
    alpha=min(9.,floor(clamp(alpha,0.,1.)*10.))*.1;
    float radius=aProperties.y*clamp(uPixels.x/650.,.72,1.35)*(1.+(1.-capture)*impact*.45);
    float diameter=2.*radius*uPixels.y;
    gl_PointSize=max(1.,diameter);vAlpha=alpha*min(1.,diameter*diameter);
    gl_Position=vec4(aMotion.x*(2.*.97/(16./9.)),-aMotion.y*(2.*.97),0.,1.);
}
