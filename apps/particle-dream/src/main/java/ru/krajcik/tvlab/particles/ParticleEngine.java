package ru.krajcik.tvlab.particles;

/** CPU reference for the GPU integrator, plus the shared low-resolution flow-field controller. */
public final class ParticleEngine {
    static final int STATE_STRIDE = 6, PROPERTY_STRIDE = 6, TARGET_STRIDE = 4;
    final int count;
    final float[] state, properties, targets;
    FlowField field = new FlowField();
    private final float[][] scenes;
    private final float cycle;
    private double elapsed;
    private int scene;
    float time, phase, motion, dt, flowTime;

    public ParticleEngine(int count, float[][] scenes, float cycleSeconds, long seed) {
        if (count <= 0 || scenes.length == 0 || !Float.isFinite(cycleSeconds) || cycleSeconds < 8)
            throw new IllegalArgumentException("Invalid simulation configuration");
        for (float[] points : scenes) {
            if (points.length < 4 || points.length % 4 != 0)
                throw new IllegalArgumentException("Targets must contain x, y, light, edge");
            for (float point : points) if (!Float.isFinite(point)) throw new IllegalArgumentException("Non-finite target");
        }
        this.count = count; this.scenes = scenes; cycle = cycleSeconds;
        state = new float[count * STATE_STRIDE];
        properties = new float[count * PROPERTY_STRIDE];
        targets = new float[count * TARGET_STRIDE];
        // Stable reference random function; seed only shifts the initial free-flow distribution.
        long epoch = Math.floorMod(seed, 10000);
        for (int i = 0; i < count; i++) {
            int s = i * 6;
            state[s] = (FlowField.random(i * 3 + epoch + 101) - .5f) * FlowField.EXTENT_X * 2;
            state[s + 1] = (FlowField.random(i * 3 + epoch + 503) - .5f) * FlowField.EXTENT_Y * 2;
            int id = i * 11;
            properties[s] = FlowField.random(id + 7);
            properties[s + 1] = (.17f + FlowField.random(id + 57) * .17f) * 1.21f;
            properties[s + 2] = FlowField.random(id + 73);
            properties[s + 3] = FlowField.random(id + 113) > .945f ? 1 : 0;
            properties[s + 4] = (FlowField.random(id + 19) - .5f) * .0024f;
            properties[s + 5] = (FlowField.random(id + 31) - .5f) * .0024f;
        }
        selectScene(0);
    }

    public int sceneIndex() { return scene; }
    float cycleSeconds() { return cycle; }

    void restartAfterContextLoss() {
        elapsed=0;time=phase=motion=dt=flowTime=0;
        field=new FlowField();selectScene(0);
    }

    private void selectScene(int index) {
        scene = index;
        float[] source = scenes[index];
        if(source.length>=targets.length){System.arraycopy(source,0,targets,0,targets.length);return;}
        for (int i = 0; i < targets.length; i += 4)
            System.arraycopy(source, i % source.length, targets, i, 4);
    }

    /** Advances only the global state. Per-particle work normally runs in GLES transform feedback. */
    void advance(float seconds) { advance(seconds, false); }

    private void advance(float seconds, boolean cpuField) {
        dt = Float.isFinite(seconds) && seconds > 0 ? Math.min(seconds, .045f) : 0;
        elapsed += dt; time = (float) elapsed;
        float peak = Math.min(4, cycle * .32f), firstStart = 15 - peak;
        long appearance = Math.max(0, (long) Math.floor((elapsed - firstStart) / cycle));
        int next = (int) (appearance % scenes.length);
        if (next != scene) selectScene(next);
        phase = (float) (elapsed - (firstStart + appearance * cycle));
        motion = .18f + .82f * FlowField.smooth(0, 20, time);
        float u = Math.max(0, Math.min(1, time / 20));
        flowTime = time < 20 ? .18f * time + 16.4f * (u*u*u - .5f*u*u*u*u) : time - 8.2f;
        if (cpuField) field.update(flowTime, dt * motion);
        else field.advanceVortices(flowTime, dt * motion);
    }

    public void step(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0) return;
        advance(seconds, true);
        float peak = Math.min(4, cycle * .32f), release = peak + Math.min(.9f, cycle * .22f);
        float end = Math.min(cycle * .95f, release + Math.min(.95f, cycle * .17f));
        for (int i = 0; i < count; i++) {
            int at = i*6, target = i*4;
            float x = state[at], y = state[at+1], vx = state[at+2], vy = state[at+3];
            float seed = properties[at], capture = state[at+4], impact = state[at+5];
            float local = phase - (properties[at+2]-.5f)*Math.min(.25f,cycle*.04f);
            float likeness = FlowField.smooth(0,peak,local)*(1-FlowField.smooth(release,end,local));
            float desired = properties[at+3] > .5f ? 0 : Math.min(.99f,likeness*1.0626f*(.9f+.1f*(float)Math.sin(time*2.7f+seed*9)));
            capture += (desired-capture)*(1-(float)Math.exp(-dt*(desired<capture?7:2.8f)));
            if (capture < .00001f) capture = 0;
            float gx = Math.max(0,Math.min(63.9999f,(x+FlowField.EXTENT_X)/(2*FlowField.EXTENT_X)*64));
            float gy = Math.max(0,Math.min(47.9999f,(y+FlowField.EXTENT_Y)/(2*FlowField.EXTENT_Y)*48));
            int ix = (int)gx, iy = (int)gy, k = (iy*65+ix)*2;
            float u = gx-ix, v = gy-iy;
            float fx = sample(k,u,v), fy = sample(k+1,u,v);
            float px = targets[target]+properties[at+4], py = targets[target+1]+properties[at+5];
            float tx = px+.023f*(float)Math.sin(time*.29f)+.011f*(float)Math.sin(time*2.8f+seed*31+py*18);
            float ty = py-.02f+.019f*(float)Math.cos(time*.23f)+.011f*(float)Math.cos(time*2.4f+seed*27+px*19);
            float freedom = .28f+.72f*(1-capture)*(1-capture), pull = 30*capture*capture*capture;
            float damping = 6.1f*capture, layer = .8f+(int)(seed*4)*.15f, response=1.8f+(seed%.25f)*4;
            vx += ((fx*layer*motion-vx)*response*freedom+(tx-x)*pull-vx*damping)*dt;
            vy += ((fy*layer*motion-vy)*response*freedom+(ty-y)*pull-vy*damping)*dt;
            float velocity = (float)Math.hypot(vx,vy);
            if (velocity>2.5f) { vx*=2.5f/velocity; vy*=2.5f/velocity; }
            x+=vx*dt; y+=vy*dt; impact*=(float)Math.exp(-dt*5);
            if (Math.abs(x)>FlowField.EXTENT_X) {
                x=Math.copySign(FlowField.EXTENT_X,x); impact=Math.min(1,Math.abs(vx)*1.4f);
                vx=-Math.copySign(Math.abs(vx)*.83f,x); vy+=(seed-.5f)*.18f;
            }
            if (Math.abs(y)>FlowField.EXTENT_Y) {
                y=Math.copySign(FlowField.EXTENT_Y,y); impact=Math.max(impact,Math.min(1,Math.abs(vy)*1.4f));
                vy=-Math.copySign(Math.abs(vy)*.83f,y); vx+=(seed-.5f)*.18f;
            }
            state[at]=x; state[at+1]=y; state[at+2]=vx; state[at+3]=vy; state[at+4]=capture; state[at+5]=impact;
        }
    }

    private float sample(int k,float u,float v) {
        float[] f=field.values;
        return (f[k]*(1-u)+f[k+2]*u)*(1-v)+(f[k+130]*(1-u)+f[k+132]*u)*v;
    }
}
