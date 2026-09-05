package ru.krajcik.tvlab.particles;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class ParticleRenderer implements GLSurfaceView.Renderer {
    private final ParticleEngine engine;
    private final FloatBuffer particles, quad;
    private final float[] positions;
    private int pointProgram, quadProgram, buffer, framebuffer, texture;
    private int pointSizeUniform, pointAlphaUniform, quadModeUniform, quadFadeUniform, samplerUniform;
    private int width, height, renderWidth, renderHeight;
    private long lastFrame;

    ParticleRenderer(ParticleEngine engine) {
        this.engine = engine;
        positions = new float[engine.x.length * 2];
        particles = floats(positions.length);
        quad = floats(8);
        quad.put(new float[] {-1, -1, 1, -1, -1, 1, 1, 1}).position(0);
    }

    @Override public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        // A new EGL context owns fresh objects; old names must never be deleted in this context.
        framebuffer = texture = 0;
        lastFrame = 0;
        pointProgram = program(
                "attribute vec2 aPosition; uniform float uSize; void main(){"
                + "gl_Position=vec4(aPosition,0.,1.);gl_PointSize=uSize;}",
                "precision mediump float;uniform float uAlpha;void main(){"
                + "vec2 p=gl_PointCoord-vec2(.5);float a=1.-smoothstep(.06,.25,dot(p,p));"
                + "gl_FragColor=vec4(.84,.94,1.,a*uAlpha);}");
        quadProgram = program(
                "attribute vec2 aPosition; varying vec2 vUv;void main(){"
                + "vUv=aPosition*.5+.5;gl_Position=vec4(aPosition,0.,1.);}",
                "precision mediump float;varying vec2 vUv;uniform sampler2D uTexture;"
                + "uniform int uMode;uniform float uFade;void main(){"
                + "if(uMode==0)gl_FragColor=vec4(0.,0.,0.,uFade);"
                + "else gl_FragColor=vec4(texture2D(uTexture,vUv).rgb,1.);}");
        pointSizeUniform = GLES20.glGetUniformLocation(pointProgram, "uSize");
        pointAlphaUniform = GLES20.glGetUniformLocation(pointProgram, "uAlpha");
        quadModeUniform = GLES20.glGetUniformLocation(quadProgram, "uMode");
        quadFadeUniform = GLES20.glGetUniformLocation(quadProgram, "uFade");
        samplerUniform = GLES20.glGetUniformLocation(quadProgram, "uTexture");
        int[] ids = new int[1];
        GLES20.glGenBuffers(1, ids, 0); buffer = ids[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, positions.length * 4, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glClearColor(0, 0, 0, 1);
    }

    @Override public void onSurfaceChanged(GL10 unused, int width, int height) {
        this.width = width; this.height = height;
        float scale = Math.min(1, Math.min(1280f / width, 720f / height));
        renderWidth = Math.max(1, Math.round(width * scale));
        renderHeight = Math.max(1, Math.round(height * scale));
        if (texture != 0) GLES20.glDeleteTextures(1, new int[] {texture}, 0);
        if (framebuffer != 0) GLES20.glDeleteFramebuffers(1, new int[] {framebuffer}, 0);
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0); texture = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, renderWidth, renderHeight,
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glGenFramebuffers(1, ids, 0); framebuffer = ids[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, texture, 0);
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Particle trail framebuffer is unavailable");
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    @Override public void onDrawFrame(GL10 unused) {
        if (width == 0 || height == 0) return;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 1f / 30 : Math.min(.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        engine.step(dt);
        for (int i = 0; i < engine.x.length; i++) {
            positions[i * 2] = engine.x[i] / ParticleEngine.ASPECT;
            positions[i * 2 + 1] = engine.y[i];
        }
        particles.clear(); particles.put(positions).position(0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
        GLES20.glViewport(0, 0, renderWidth, renderHeight);
        // Use an explicit texture for trails; EGL swap-buffer preservation is not portable.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(quadProgram);
        GLES20.glUniform1i(quadModeUniform, 0);
        GLES20.glUniform1f(quadFadeUniform, 1 - (float) Math.exp(-dt * 8));
        drawQuad();

        GLES20.glUseProgram(pointProgram);
        GLES20.glUniform1f(pointSizeUniform, Math.max(1.4f, renderHeight / 360f));
        GLES20.glUniform1f(pointAlphaUniform, .3f * (float) Math.sqrt(16000f / engine.x.length));
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, positions.length * 4, particles);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, engine.x.length);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(quadProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(samplerUniform, 0);
        GLES20.glUniform1i(quadModeUniform, 1);
        drawQuad();
    }

    private void drawQuad() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        quad.position(0);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, quad);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    private static FloatBuffer floats(int count) {
        return ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    private static int program(String vertex, String fragment) {
        int vs = shader(GLES20.GL_VERTEX_SHADER, vertex), fs = shader(GLES20.GL_FRAGMENT_SHADER, fragment);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs); GLES20.glAttachShader(program, fs);
        GLES20.glBindAttribLocation(program, 0, "aPosition");
        GLES20.glLinkProgram(program);
        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs);
        if (status[0] == 0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
        return program;
    }

    private static int shader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) throw new IllegalStateException(GLES20.glGetShaderInfoLog(shader));
        return shader;
    }
}
