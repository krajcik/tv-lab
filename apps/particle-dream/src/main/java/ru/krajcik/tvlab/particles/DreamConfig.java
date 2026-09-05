package ru.krajcik.tvlab.particles;

import android.content.Context;
import android.content.SharedPreferences;

final class DreamConfig {
    static final String DEFAULT_PHRASES = "ТИШЕ\nЗДЕСЬ И СЕЙЧАС\nДЫШИ";
    static final String IMAGE_FILE = "custom-image.png";
    final String phrases;
    final boolean text, pictures;
    final int count;
    final float cycleSeconds;

    DreamConfig(Context context) {
        SharedPreferences p = preferences(context);
        phrases = p.getString("phrases", DEFAULT_PHRASES);
        text = p.getBoolean("text", false);
        pictures = p.getBoolean("pictures", true);
        int requested = p.getInt("count", 200000);
        count = requested == 50000 || requested == 100000 ? requested : 200000;
        cycleSeconds = p.getBoolean("long_cycle", false) ? 32 : 18.75f;
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("particles", Context.MODE_PRIVATE);
    }
}
