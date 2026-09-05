package ru.krajcik.tvlab.particles;

/** Dependency-free host checks, run with tools/test-engine.sh. */
public final class ParticleEngineTest {
    public static void main(String[] args) {
        float[][] targets = {{0, 0}, {.6f, .3f}};
        ParticleEngine a = new ParticleEngine(1000, targets, 24, 42);
        ParticleEngine b = new ParticleEngine(1000, targets, 24, 42);
        for (int i = 0; i < 400; i++) { a.step(1f / 30); b.step(1f / 30); }
        require(a.morphAmount() == 1, "A scene must hold its target");
        double distance = 0;
        int count = 0;
        for (int i = 0; i < a.x.length; i++) {
            require(a.x[i] == b.x[i] && a.y[i] == b.y[i], "Seeded simulation must be deterministic");
            if (i % 13 == 0) continue;
            distance += Math.hypot(a.x[i], a.y[i]); count++;
        }
        require(distance / count < .16, "Particles must converge to the image during hold, distance=" + distance / count);
        for (int i = 400; i < 750; i++) a.step(1f / 30);
        require(a.sceneIndex() == 1, "Next cycle must select the next scene");
        require(a.morphAmount() == 0, "New cycle must begin with free flow");
        for (int i = 0; i < 18000; i++) {
            a.step(1f / 30);
            for (int j = 0; j < a.x.length; j++)
                require(Float.isFinite(a.x[j]) && Float.isFinite(a.y[j])
                        && Math.abs(a.x[j]) <= ParticleEngine.ASPECT && Math.abs(a.y[j]) <= 1,
                        "Simulation must remain finite and bounded after ten minutes");
        }
        ParticleEngine c = new ParticleEngine(100, targets, 24, 4);
        c.step(Float.NaN); c.step(Float.POSITIVE_INFINITY); c.step(-1); c.step(100000);
        require(c.sceneIndex() == 0, "A resume gap must not skip scenes");
        try { new ParticleEngine(10, new float[][] {{Float.NaN, 0}}, 24, 1); throw new AssertionError("Invalid target accepted"); }
        catch (IllegalArgumentException expected) { /* expected */ }
        System.out.println("PASS: deterministic motion, target convergence, scene cycle, 10-minute stability, resume gap, invalid targets");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
