package ru.krajcik.tvlab.particles;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class SceneFactory {
    private static final int WIDTH = 640, HEIGHT = 360;

    static float[][] create(Context context, DreamConfig config) {
        List<float[]> scenes = new ArrayList<>();
        if (config.text) {
            for (String line : config.phrases.split("\n")) {
                String text = line.trim();
                if (text.isEmpty()) continue;
                float[] points = text(text.substring(0, Math.min(text.length(), 100)));
                if (points != null) scenes.add(points);
                if (scenes.size() == 12) break;
            }
        }
        if (config.pictures) {
            int before = scenes.size();
            File image = new File(context.getFilesDir(), DreamConfig.IMAGE_FILE);
            if (image.isFile()) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(image.getAbsolutePath(), options);
                options.inSampleSize = 1;
                while (options.outWidth / options.inSampleSize > WIDTH || options.outHeight / options.inSampleSize > HEIGHT)
                    options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                Bitmap bitmap = BitmapFactory.decodeFile(image.getAbsolutePath(), options);
                if (bitmap != null) {
                    try {
                        float[] points = sample(bitmap, true);
                        if (points != null) scenes.add(points);
                    } finally { bitmap.recycle(); }
                }
            }
            if (scenes.size() == before) addPets(context, scenes);
        }
        if (scenes.isEmpty()) addPets(context, scenes);
        return scenes.toArray(new float[0][]);
    }

    private static float[] text(String value) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(84);
        float width = paint.measureText(value);
        if (width > WIDTH - 64) paint.setTextSize(84 * (WIDTH - 64) / width);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(value, WIDTH / 2f, HEIGHT / 2f - (paint.ascent() + paint.descent()) / 2, paint);
        try { return sample(bitmap, false); } finally { bitmap.recycle(); }
    }

    private static void addPets(Context context, List<float[]> scenes) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 2;
        Bitmap atlas = BitmapFactory.decodeResource(context.getResources(), R.drawable.pets, options);
        if (atlas == null) throw new IllegalStateException("Missing built-in animal atlas");
        // Boundaries follow the black gutters in the generated atlas, preserving paws and tails.
        float[] edges = {0, .36f, .655f, 1};
        try {
            for (int i = 0; i < 3; i++) {
                int left = Math.round(atlas.getWidth() * edges[i]);
                int right = Math.round(atlas.getWidth() * edges[i + 1]);
                Bitmap animal = Bitmap.createBitmap(atlas, left, 0, right - left, atlas.getHeight());
                try {
                    float[] points = sample(animal, true);
                    if (points != null) scenes.add(points);
                } finally { animal.recycle(); }
            }
        } finally { atlas.recycle(); }
        if (scenes.isEmpty()) throw new IllegalStateException("Empty built-in animal atlas");
    }

    /** Sample a luminance-weighted cloud; image backgrounds are reduced by a simple edge term. */
    private static float[] sample(Bitmap bitmap, boolean image) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        Random random = new Random(71);
        List<Integer> candidates = new ArrayList<>();
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            int at = y * w + x;
            float luma = luminance(pixels[at]);
            float weight = luma;
            if (image) {
                float edge = Math.abs(luma - luminance(pixels[at + 1]))
                        + Math.abs(luma - luminance(pixels[at + w]));
                weight = Math.min(1, luma * luma + edge * .3f);
            }
            if (weight > .06f && random.nextFloat() < weight) candidates.add(at);
        }
        if (candidates.isEmpty()) return null;
        float[] result = new float[200000 * 4];
        float scale = .96f / Math.max(w, h);
        for (int i = 0; i < result.length; i += 4) {
            int at = candidates.get(random.nextInt(candidates.size()));
            result[i] = ((at % w) - w / 2f + random.nextFloat() - .5f) * scale;
            result[i + 1] = ((at / w) - h / 2f + random.nextFloat() - .5f) * scale;
            result[i + 2] = image ? luminance(pixels[at]) : 1;
            result[i + 3] = 1 - FlowField.smooth(.36f, .50f, result[i + 1]);
        }
        return result;
    }

    private static float luminance(int pixel) {
        return Color.alpha(pixel) / 255f * (.2126f * Color.red(pixel)
                + .7152f * Color.green(pixel) + .0722f * Color.blue(pixel)) / 255f;
    }
}
