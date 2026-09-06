// Artistic force fields, not an astrophysics or granular-material simulator.
// Everything is O(particles); no pairwise particle interactions or CPU readbacks.
vec2 rotateMode(vec2 p,float a){float c=cos(a),s=sin(a);return vec2(c*p.x-s*p.y,s*p.x+c*p.y);}
vec2 avoidDisc(vec2 p,vec2 velocity,vec2 center,float radius){
    vec2 d=p-center;float r=length(d);vec2 n=d/max(r,.0001);
    return n*(max(0.,radius+.04-r)*55.-min(0.,dot(velocity,n))*4.*(1.-smoothstep(radius,radius+.05,r)));
}
vec2 modeForce(int mode,vec2 p,vec2 velocity,vec2 random,vec2 field,float t,vec2 bounds){
    float seed=random.x,z=random.y,pi=3.14159265359;
    if(mode==1){ // A breath every ~30s, precession/shape evolution over many minutes.
        float breath=.5+.5*sin(t*.21+.6*sin(t*.0071));
        float radius=(.15+.78*breath)*sqrt(seed);
        float angle=z*2.*pi+t*.014+.3*sin(t*.0017);
        vec2 target=vec2(cos(angle),sin(angle))*bounds*.96*radius;
        target+=field*.018*(1.-breath);
        return (target-p)*3.1-velocity*2.7+field*.08;
    }
    if(mode==2){ // The shape is visible only through the dust flowing around it.
        vec2 center=vec2(.12*sin(t*.0063),.06*cos(t*.0041));
        float radius=.18+.035*sin(t*.0037);
        vec2 shape=vec2(1.55,1.),q=p/shape;
        float distance=length(q),orbit=.22+.23*sqrt(seed);
        vec2 desired=vec2(-q.y,q.x)*shape*.22;
        vec2 force=(desired-velocity)*2.6-p*.0484;
        force+=q/max(distance,.001)*shape*(orbit-distance)*1.4+field*.18;
        force+=.025*vec2(sin(t*.71+seed*103.),cos(t*.63+z*79.));
        force+=avoidDisc(p,velocity,center,radius);
        vec2 satellite=center+rotateMode(vec2(.27,0),t*.008);
        force+=avoidDisc(p,velocity,satellite,.065+.018*sin(t*.005));
        return force;
    }
    if(mode==3){ // The field grid contains echoes of real vortex/wall collisions.
        vec2 home=(random*2.-1.)*bounds*.90;
        home+=vec2(.04*sin(t*.009+seed*17.),.035*cos(t*.007+z*23.));
        vec2 thermal=.04*vec2(sin(t*.67+seed*137.),cos(t*.73+z*119.));
        return (field*.72-velocity)*2.0+(home-p)*.55+thermal;
    }
    if(mode==4){ // Rotating gravity, bounded piles and slowly changing dune profiles.
        float leg=t/85.+.10*sin(t*.0017);
        float angle=(floor(leg)+smoothstep(.58,.98,fract(leg)))*pi*.5;
        vec2 gravity=vec2(sin(angle),cos(angle)),side=vec2(gravity.y,-gravity.x);
        float span=dot(abs(side),bounds)*.94,q=(seed*2.-1.)*span;
        vec2 foot=side*q;
        vec2 ray=(bounds-sign(gravity)*foot)/max(abs(gravity),vec2(.0001));
        float floorDepth=min(ray.x,ray.y);
        vec2 reverse=(bounds+sign(gravity)*foot)/max(abs(gravity),vec2(.0001));
        float space=max(.001,floorDepth+min(reverse.x,reverse.y));
        float dunes=.10+.045*sin(q*9.+t*.0023)+.035*sin(q*17.-t*.0031);
        float depth=min(space*.6,dunes)*pow(z,.65);
        vec2 target=foot+gravity*(floorDepth-depth-.008);
        float nearFloor=1.-smoothstep(.04,.25,dot(target-p,gravity));
        return gravity*.6*(1.-nearFloor)+(target-p)*mix(1.2,8.,nearFloor)-velocity*mix(1.1,4.8,nearFloor)+field*.012;
    }
    if(mode==5){ // Different snow crystals grow and melt over five-minute chapters.
        float chapter=floor(t/300.),age=mod(t,300.);
        float growth=smoothstep(8.,145.,age)*(1.-smoothstep(215.,290.,age));
        float arm=floor(seed*6.),variant=fract(seed*61.37+chapter*.137);
        float r=z*.37,branch=mod(floor(variant*12.),2.)*2.-1.;
        vec2 local=vec2(r,0);
        if(variant>.36){float anchor=(1.+floor(z*5.))*.051;float length=(.02+.08*(1.-z))*fract(variant*17.);local=vec2(anchor+length*.5,branch*length*.866);}
        local+=vec2(fract(seed*191.7)-.5,fract(z*139.3)-.5)*.0025;
        vec2 target=rotateMode(local,arm*pi/3.+t*.0013+chapter*.21);
        float frozen=1.-smoothstep(growth-.05,growth+.05,z);
        return mix((field*.55-velocity)*1.8,(target-p)*13.-velocity*6.,frozen);
    }
    if(mode==6){ // Two distinguishable populations: separation, ribbons, interweaving.
        float group=seed<.5?-1.:1.;
        float weave=.5-.5*cos(t*.018+.25*sin(t*.0029));
        float angle=z*2.*pi+group*t*.085;
        float spread=(fract(seed*97.17)-.5)*.024;
        vec2 target=vec2(.78*cos(angle),group*.22*(1.-weave)+.08*sin(angle)+weave*.22*sin(angle*2.+group*.8+sin(t*.003)));
        target.y+=spread;target=rotateMode(target,.07*sin(t*.0019));
        return (target-p)*4.-velocity*3.3+field*.025;
    }
    if(mode==7){ // Differential spiral rotation; two nuclei separate, braid and merge.
        float group=seed<.7?-1.:1.,s=seed<.7?seed/.7:(seed-.7)/.3;
        float r=(.012+.44*pow(s,1.25))*(.94+.06*sin(z*431.3));
        float separation=.42*(.5-.5*cos(t*.0062));
        vec2 center=rotateMode(vec2(group*separation,0),t*.003);
        float arm=floor(z*3.),theta=arm*2.*pi/3.+r*(8.+sin(t*.0013))+t*.065/(.3+r)+(fract(z*3.)-.5)*(.38+1.1*s);
        if(fract(seed*101.17)<.25)theta=z*2.*pi+t*.04/(.3+r);
        if(group>0.)theta=-theta+t*.014;
        vec2 target=center+rotateMode(vec2(cos(theta)*r,sin(theta)*r*.73),t*.0017);
        return (target-p)*3.-velocity*2.9+field*.008;
    }
    // Accretion disk and two recycling jets. No particle deletion or teleportation.
    float axis=.12*sin(t*.0023)+.06*sin(t*.00091);
    vec2 target;
    if(seed<.16){
        float side=seed<.08?-1.:1.,a=fract(z+t/85.),u=1.-a;
        vec2 start=vec2(.008,.10),c1=vec2(.008,bounds.y*.94),c2=vec2(bounds.x*.80,bounds.y*.91),end=vec2(.42,.02);
        target=(u*u*u*start+3.*u*u*a*c1+3.*u*a*a*c2+a*a*a*end)*side;
        target.x+=(fract(seed*131.)-.5)*.018;
    }else{
        float r=.14+.36*sqrt(fract(seed*1.17));
        float theta=z*2.*pi+t*.24/(.35+seed)+r*2.;
        target=vec2(cos(theta)*r,sin(theta)*r*.65);
    }
    target=rotateMode(target,axis);
    return (target-p)*4.5-velocity*3.4+avoidDisc(p,velocity,vec2(0),.072);
}
