package ru.krajcik.tvlab.particles;

/** Stable stored IDs; zero always means the existing image/quote animation. */
final class ParticleModes {
    static final int COUNT=8, ALL=(1<<COUNT)-1;
    static final String[] NAMES={"Дыхание","Невидимая скульптура","Память стенок","Песок без верха и низа","Рождение кристалла","Два течения","Рождение галактики","Чёрная дыра"};
    static final int[] DURATIONS={900,1800,3600,10800,0};
    static int validDuration(int value){for(int seconds:DURATIONS)if(seconds==value)return value;return 3600;}
    static float blend(float phase,float duration){return FlowField.smooth(0,20,phase)*(Float.isInfinite(duration)?1:1-FlowField.smooth(duration-20,duration,phase));}
    static void validate(int mode){if(mode<0||mode>COUNT)throw new IllegalArgumentException("Unknown particle mode");}
}
