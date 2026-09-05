package ru.krajcik.tvlab.particles;

import android.content.Context;
import android.content.SharedPreferences;

final class DreamConfig {
    static final String DEFAULT_PHRASES = "ТИШЕ\nЗДЕСЬ И СЕЙЧАС\nДЫШИ";
    static final String IMAGE_FILE = "custom-image.png";
    static final String[] FONTS = {"serif", "sans-serif-light", "sans-serif-condensed"};
    final String phrases, font;
    final boolean text, pictures, quotes;
    final int count;
    final float cycleSeconds;

    DreamConfig(Context context) {
        SharedPreferences p = preferences(context);
        phrases = p.getString("phrases", DEFAULT_PHRASES);
        String chosen = p.getString("font", "serif");
        font = chosen.equals(FONTS[1]) || chosen.equals(FONTS[2]) ? chosen : FONTS[0];
        text = p.getBoolean("text", false);
        quotes = p.getBoolean("quotes", true);
        pictures = p.getBoolean("pictures", true);
        int requested = p.getInt("count", 200000);
        count = requested == 50000 || requested == 100000 ? requested : 200000;
        cycleSeconds = p.getBoolean("long_cycle", false) ? 32 : 18.75f;
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("particles", Context.MODE_PRIVATE);
    }
}
