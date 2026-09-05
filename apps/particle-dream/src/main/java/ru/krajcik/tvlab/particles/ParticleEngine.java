package ru.krajcik.tvlab.particles;

import java.util.Random;

/** Pure Java simulation. Coordinates are in a 16:9 world, independent of output resolution. */
public final class ParticleEngine {
    public static final float ASPECT = 16f / 9f;
    private static final int GRID_X = 64, GRID_Y = 36, STRIDE = GRID_X + 1;
    public final float[] x, y;
    private final float[] vx, vy, fieldX = new float[STRIDE * (GRID_Y + 1)],
            fieldY = new float[STRIDE * (GRID_Y + 1)];
    private final float[][] scenes;
    private final float cycleSeconds;
    private double elapsed;
    private int scene;
    private float morph;

    public ParticleEngine(int count, float[][] scenes, float cycleSeconds, long seed) {
        if (count <= 0 || scenes.length == 0 || !Float.isFinite(cycleSeconds) || cycleSeconds < 8)
            throw new IllegalArgumentException("Invalid simulation configuration");
        for (float[] points : scenes) {
            if (points.length < 2 || points.length % 2 != 0)
                throw new IllegalArgumentException("Empty or unpaired target points");
            for (float p : points) if (!Float.isFinite(p))
                throw new IllegalArgumentException("Non-finite target point");
        }
        this.scenes = scenes;
        this.cycleSeconds = cycleSeconds;
        x = new float[count]; y = new float[count];
        vx = new float[count]; vy = new float[count];
        Random random = new Random(seed);
        for (int i = 0; i < count; i++) {
            x[i] = (random.nextFloat() * 2 - 1) * ASPECT;
            y[i] = random.nextFloat() * 2 - 1;
        }
    }

    public int sceneIndex() { return scene; }
    public float morphAmount() { return morph; }

    public void step(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0) return;
        // A resumed view must not simulate all the time it spent asleep.
        float dt = Math.min(seconds, 0.05f);
        elapsed += dt;
        scene = (int) ((long) (elapsed / cycleSeconds) % scenes.length);
        float phase = (float) ((elapsed % cycleSeconds) / cycleSeconds);
        morph = phase < .32f ? 0 : phase < .51f ? smooth((phase - .32f) / .19f)
                : phase < .73f ? 1 : phase < .94f ? 1 - smooth((phase - .73f) / .21f) : 0;
        float time = (float) (elapsed % 10000);
        updateField(time);
        float[] target = scenes[scene];
        float damping = (float) Math.exp(-dt * (.65f + morph * 3.2f));
        float shiftX = .07f * (float) Math.sin(time * .13f);
        float shiftY = .04f * (float) Math.cos(time * .17f);
        for (int i = 0; i < x.length; i++) {
            float gx = Math.max(0, Math.min(GRID_X - .001f, (x[i] / ASPECT + 1) * .5f * GRID_X));
            float gy = Math.max(0, Math.min(GRID_Y - .001f, (y[i] + 1) * .5f * GRID_Y));
            int ix = (int) gx, iy = (int) gy, at = iy * STRIDE + ix;
            float fx = gx - ix, fy = gy - iy;
            float ax = interpolate(fieldX, at, fx, fy), ay = interpolate(fieldY, at, fx, fy);
            int point = (i % (target.length / 2)) * 2;
            // Keep a sparse layer moving around the image so a scene never becomes a static slide.
            float pull = i % 13 == 0 ? 0 : morph;
            ax = ax * (1 - pull * .96f) + (target[point] + shiftX - x[i]) * pull * 12;
            ay = ay * (1 - pull * .96f) + (target[point + 1] + shiftY - y[i]) * pull * 12;
            vx[i] = (vx[i] + ax * dt) * damping;
            vy[i] = (vy[i] + ay * dt) * damping;
            x[i] += vx[i] * dt; y[i] += vy[i] * dt;
            // Wrap the field so outward flow cannot accumulate into stationary bright screen edges.
            if (x[i] > ASPECT) x[i] -= ASPECT * 2;
            if (x[i] < -ASPECT) x[i] += ASPECT * 2;
            if (y[i] > 1) y[i] -= 2;
            if (y[i] < -1) y[i] += 2;
        }
    }

    private void updateField(float time) {
        float cx = .72f * (float) Math.sin(time * .12f), cy = .35f * (float) Math.cos(time * .15f);
        for (int j = 0; j <= GRID_Y; j++) {
            float py = j * 2f / GRID_Y - 1;
            for (int i = 0; i <= GRID_X; i++) {
                float px = (i * 2f / GRID_X - 1) * ASPECT;
                float dx = px - cx, dy = py - cy;
                float spin = .3f / (.3f + dx * dx + dy * dy);
                int at = j * STRIDE + i;
                fieldX[at] = .27f * (float) Math.sin(py * 4 + time * .31f)
                        + .14f * (float) Math.cos(px * 3 - time * .23f) - dy * spin;
                fieldY[at] = .22f * (float) Math.cos(px * 3.5f - time * .26f)
                        + .12f * (float) Math.sin(py * 5 + time * .19f) + dx * spin;
            }
        }
    }

    private static float interpolate(float[] values, int at, float fx, float fy) {
        float a = values[at] + (values[at + 1] - values[at]) * fx;
        float b = values[at + STRIDE] + (values[at + STRIDE + 1] - values[at + STRIDE]) * fx;
        return a + (b - a) * fy;
    }

    private static float smooth(float t) { return t * t * (3 - 2 * t); }
}
