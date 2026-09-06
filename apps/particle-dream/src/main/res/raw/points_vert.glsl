#version 300 es
precision highp float;
layout(location=0) in vec4 aMotion;
layout(location=1) in vec2 aCaptureImpact;
layout(location=2) in vec4 aProperties;
layout(location=4) in vec4 aTarget;
uniform float uText;
uniform vec4 uMode;
uniform vec2 uPixels; // logical scale, pixel ratio
out float vAlpha;
void main(){
    float capture=aCaptureImpact.x,impact=aCaptureImpact.y,seed=aProperties.x;
    float alpha=mix(.13+seed*.27,aTarget.z*aTarget.w*.89,capture)+impact*.22*(1.-capture);
    float coarse=min(9.,floor(clamp(alpha,0.,1.)*10.))*.1;
    float held=pow(clamp(aTarget.z,0.,1.),.65)*aTarget.w;
    alpha=mix(coarse,held,smoothstep(.7,.98,capture));
    float radius=aProperties.y*clamp(uPixels.x/650.,.72,1.35)*(1.+(1.-capture)*impact*.45);
    radius*=mix(1.,uText>.5?1.16:1.08,smoothstep(.7,.98,capture));
    if(uMode.x>.5){
        float artAlpha=.24+seed*.38;
        if(uMode.x>5.5&&uMode.x<6.5){artAlpha=seed<.5?.62:.29;radius*=seed<.5?1.05:.85;}
        if(uMode.x>7.5){artAlpha*=smoothstep(.068,.105,length(aMotion.xy));if(seed<.16)artAlpha=.64;}
        alpha=mix(alpha,artAlpha+impact*.25,uMode.w);
    }
    float diameter=2.*radius*uPixels.y;
    gl_PointSize=max(1.,diameter);vAlpha=alpha*min(1.,diameter*diameter);
    gl_Position=vec4(aMotion.x*(2.*.97/(16./9.)),-aMotion.y*(2.*.97),0.,1.);
}
