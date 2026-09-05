package ru.krajcik.tvlab.particles;

/** A lightweight recipe, not an allocated cloud. Shared recipes have shared cache identity. */
interface SceneSource {
    float[] load() throws Exception;
}
