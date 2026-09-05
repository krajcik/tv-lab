package ru.krajcik.tvlab.particles;

/** Multi-scale incompressible flow with eight finite-lived, colliding vortices. */
final class FlowField {
    static final int CELLS_X = 64, CELLS_Y = 48, WIDTH = 65, HEIGHT = 49;
    static final float EXTENT_X = (16f / 9f) / (2 * .97f) - .006f;
    static final float EXTENT_Y = 1 / (2 * .97f) - .006f;
    final float[] values = new float[WIDTH * HEIGHT * 2];
    final float[] centers = new float[32], shapes = new float[32], seeds = new float[32];
    private final Vortex[] vortices = new Vortex[8];
    private int generation;

    private static final class Vortex {
        float x, y, vx, vy, radius, spin, life, age, shock, cooldown, seed;
    }

    FlowField() { for (int i = 0; i < vortices.length; i++) spawn(i, 0); }

    static float random(double n) {
        double value = Math.sin(n * 127.1 + 311.7) * 43758.5453123;
        return (float) (value - Math.floor(value));
    }

    static float smooth(float start, float end, float value) {
        float t = Math.max(0, Math.min(1, (value - start) / (end - start)));
        return t * t * (3 - 2 * t);
    }

    private void spawn(int index, float time) {
        Vortex v = new Vortex();
        v.seed = index * 131 + generation++ * 37 + (float) Math.floor(time * 5);
        double angle = random(v.seed + 19) * Math.PI * 2;
        v.x = (random(v.seed + 1) - .5f) * EXTENT_X * 1.65f;
        v.y = (random(v.seed + 3) - .5f) * EXTENT_Y * 1.65f;
        v.vx = (float) Math.cos(angle) * (.09f + random(v.seed + 11) * .10f);
        v.vy = (float) Math.sin(angle) * (.09f + random(v.seed + 13) * .10f);
        v.radius = .15f + random(v.seed + 5) * .17f;
        v.spin = (index % 2 == 0 ? -1 : 1) * (.055f + random(v.seed + 7) * .045f);
        v.life = 8 + random(v.seed + 17) * 10;
        vortices[index] = v;
    }

    void update(float time, float dt) { advanceVortices(time, dt); buildGrid(time); }

    void advanceVortices(float time, float dt) {
        for (int i = 0; i < vortices.length; i++) {
            Vortex v = vortices[i];
            v.age += dt; v.shock *= (float) Math.exp(-dt * 2.6f);
            v.cooldown = Math.max(0, v.cooldown - dt);
            if (v.age >= v.life) { spawn(i, time); v = vortices[i]; }
            float turn = time * (.37f + random(v.seed) * .25f) + v.seed;
            v.vx += (float) Math.cos(turn) * dt * .075f;
            v.vy += (float) Math.sin(turn * 1.17f) * dt * .075f;
            float speed = (float) Math.hypot(v.vx, v.vy);
            if (speed > .20f) { v.vx *= .20f / speed; v.vy *= .20f / speed; }
            v.x += v.vx * dt; v.y += v.vy * dt;
            float bx = EXTENT_X * .91f, by = EXTENT_Y * .91f;
            if (Math.abs(v.x) > bx) {
                v.x = Math.copySign(bx, v.x); v.vx = -Math.copySign(Math.abs(v.vx) * .95f, v.x);
                v.shock = Math.max(v.shock, .45f);
            }
            if (Math.abs(v.y) > by) {
                v.y = Math.copySign(by, v.y); v.vy = -Math.copySign(Math.abs(v.vy) * .95f, v.y);
                v.shock = Math.max(v.shock, .45f);
            }
        }
        for (int i = 0; i < vortices.length; i++) for (int j = i + 1; j < vortices.length; j++) {
            Vortex a = vortices[i], b = vortices[j];
            float dx = b.x - a.x, dy = b.y - a.y, distance = (float) Math.hypot(dx, dy);
            float overlap = (a.radius + b.radius) * .62f - distance;
            if (overlap <= 0 || distance < .00001f) continue;
            float nx = dx / distance, ny = dy / distance;
            float separation = overlap * Math.min(1, dt * 2.4f) * .5f;
            a.x -= nx * separation; a.y -= ny * separation;
            b.x += nx * separation; b.y += ny * separation;
            if (a.cooldown > 0 || b.cooldown > 0) continue;
            float impulse = .11f + overlap * .4f;
            a.vx -= nx * impulse; a.vy -= ny * impulse;
            b.vx += nx * impulse; b.vy += ny * impulse;
            a.shock = b.shock = .85f; a.cooldown = b.cooldown = 2;
            if (a.spin * b.spin < 0) {
                a.age = Math.max(a.age, a.life - 1.6f); b.age = Math.max(b.age, b.life - 2);
            } else { a.spin *= .85f; b.spin *= 1.08f; }
        }
        for (int i = 0; i < 8; i++) {
            Vortex v = vortices[i]; int k = i * 4;
            centers[k] = v.x; centers[k+1] = v.y; centers[k+2] = v.vx; centers[k+3] = v.vy;
            shapes[k] = v.radius; shapes[k+1] = v.spin;
            shapes[k+2] = smooth(0,1.7f,v.age)*(1-smooth(v.life-2,v.life,v.age));
            shapes[k+3] = v.shock; seeds[k] = v.seed;
        }
    }

    private void buildGrid(float time) {
        float q = time * .23f;
        double meanX = 0, meanY = 0;
        for (int j = 0; j < HEIGHT; j++) for (int i = 0; i < WIDTH; i++) {
            float x = (i / (float) CELLS_X * 2 - 1) * EXTENT_X;
            float y = (j / (float) CELLS_Y * 2 - 1) * EXTENT_Y;
            float fx = (float) (.055 * Math.sin(3.3*x-.43*q)*Math.cos(4.1*y+.62*q)
                    + .065*Math.sin(8.3*x+q)*Math.cos(10.7*y-.83*q)
                    + .026*Math.sin(19.1*x-1.63*q)*Math.cos(23.7*y+1.31*q));
            float fy = (float) (-.055*(3.3/4.1)*Math.cos(3.3*x-.43*q)*Math.sin(4.1*y+.62*q)
                    - .065*(8.3/10.7)*Math.cos(8.3*x+q)*Math.sin(10.7*y-.83*q)
                    - .026*(19.1/23.7)*Math.cos(19.1*x-1.63*q)*Math.sin(23.7*y+1.31*q));
            for (Vortex v : vortices) {
                float dx = x-v.x, dy = y-v.y, r2 = dx*dx+dy*dy;
                float region = (float) Math.exp(-r2/(v.radius*v.radius*3.3f));
                float life = smooth(0,1.7f,v.age)*(1-smooth(v.life-2,v.life,v.age));
                float r = (float) Math.sqrt(r2)+.001f, angle = (float) Math.atan2(dy,dx), shell = r/v.radius;
                float layers = 1 + .46f*(float)Math.sin(shell*12-angle*2-q*1.2f+v.seed)
                        + .24f*(float)Math.sin(shell*23+angle*3+q*.81f);
                float swirl = v.spin*life*region*layers/(.014f+r2);
                float shear = .042f*life*region*(float)Math.sin(shell*17-angle*3+q*1.47f);
                float ring = (1-v.shock)*v.radius*2.2f;
                float shock = v.shock*(float)Math.exp(-(r-ring)*(r-ring)/.013f)*.32f;
                fx += (-dy*swirl+v.vx*region*.9f)*.85f+dx/r*(shock+shear);
                fy += (dx*swirl+v.vy*region*.9f)*.85f+dy/r*(shock+shear);
            }
            int at = (j*WIDTH+i)*2;
            values[at] = fx; values[at+1] = fy; meanX += fx; meanY += fy;
        }
        float subtractX = (float)(meanX/(WIDTH*HEIGHT)*.9), subtractY = (float)(meanY/(WIDTH*HEIGHT)*.9);
        for (int i = 0; i < values.length; i += 2) { values[i] -= subtractX; values[i+1] -= subtractY; }
    }
}
