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
    static final int BUILTIN_IMAGES = 100;
    static final int TARGET_POINTS = 65536;
    static final int TEXT_TARGET_POINTS = 16384;

    static ScenePlaylist playlist(Context context, DreamConfig config) {
        List<SceneSource> words = new ArrayList<>();
        if (config.quotes) for (QuoteLibrary.Quote quote : QuoteLibrary.read(context))
            words.add(() -> text(quote.text, quote.author, config.font));
        if (config.text) {
            int count = 0;
            for (String line : config.phrases.split("\\n")) {
                String value = line.trim();
                if (value.isEmpty()) continue;
                String phrase = value.substring(0, Math.min(value.length(), 100));
                words.add(() -> text(phrase, null, config.font));
                if (++count == 12) break;
            }
        }
        List<SceneSource> pictures = new ArrayList<>();
        if (config.pictures) {
            File file = new File(context.getFilesDir(), DreamConfig.IMAGE_FILE);
            if (file.isFile()) pictures.add(() -> customImage(context, file));
            else pictures.addAll(builtins(context));
        }
        if (pictures.isEmpty() && words.isEmpty()) pictures.addAll(builtins(context));
        if(config.shuffle){java.util.Collections.shuffle(pictures);java.util.Collections.shuffle(words);}
        List<SceneSource> order = new ArrayList<>();
        List<Boolean> kinds = new ArrayList<>();
        for (int i=0;i<Math.max(pictures.size(),words.size());i++) {
            if (!pictures.isEmpty()) { order.add(pictures.get(i%pictures.size())); kinds.add(false); }
            if (!words.isEmpty()) { order.add(words.get(i%words.size())); kinds.add(true); }
        }
        boolean[] flags = new boolean[kinds.size()];
        for(int i=0;i<flags.length;i++)flags[i]=kinds.get(i);
        return new ScenePlaylist(order.toArray(new SceneSource[0]),flags);
    }

    private static float[] customImage(Context context, File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        options.inSampleSize = 1;
        while (options.outWidth/options.inSampleSize>WIDTH || options.outHeight/options.inSampleSize>HEIGHT) options.inSampleSize*=2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap != null) {
            try { float[] cloud=sample(bitmap,true); if(cloud!=null)return cloud; }
            finally { bitmap.recycle(); }
        }
        return atlas(context,R.drawable.pets,0,.36f);
    }

    static float[] text(String value, String author, String font) {
        Bitmap bitmap = renderText(value, author, font);
        try { return sample(bitmap, false); } finally { bitmap.recycle(); }
    }

    static Bitmap renderText(String value, String author, String font) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(font, Typeface.NORMAL));
        paint.setTextSize(48);
        List<String> lines = wrap(value, paint, WIDTH - 64);
        while (lines.size() > 3 && paint.getTextSize() > 22) {
            paint.setTextSize(paint.getTextSize() - 2);
            lines = wrap(value, paint, WIDTH - 64);
        }
        paint.setTextAlign(Paint.Align.CENTER);
        float spacing = paint.getFontSpacing() * 1.1f;
        float total = spacing * lines.size() + (author == null ? 0 : 50);
        float baseline = (HEIGHT - total) / 2 - paint.ascent();
        for (String line : lines) { canvas.drawText(line, WIDTH / 2f, baseline, paint); baseline += spacing; }
        if (author != null) {
            paint.setTypeface(Typeface.create("serif", Typeface.ITALIC));
            paint.setTextSize(24);
            canvas.drawText("— " + author, WIDTH / 2f, baseline + 18, paint);
        }
        return bitmap;
    }

    static List<String> wrap(String value, Paint paint, float width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : value.split("\\n")) {
            String line = "";
            for (String word : paragraph.trim().split("\\s+")) {
                if (word.isEmpty()) continue;
                if (!line.isEmpty() && paint.measureText(line + " " + word) > width) {
                    lines.add(line); line = "";
                }
                // A long URL or unbroken custom word must not overflow the canvas.
                while (paint.measureText(word) > width) {
                    int count = Math.max(1, paint.breakText(word, true, width, null));
                    lines.add(word.substring(0, count)); word = word.substring(count);
                }
                if (!word.isEmpty()) line = line.isEmpty() ? word : line + " " + word;
            }
            if (!line.isEmpty()) lines.add(line);
        }
        return lines;
    }

    private static List<SceneSource> builtins(Context context) {
        List<SceneSource> result=new ArrayList<>();
        for(ImageLibrary.Entry entry:ImageLibrary.read(context))result.add(()->image(context,entry));
        return result;
    }
    static float[] image(Context context,ImageLibrary.Entry entry){return atlas(context,entry.resource,entry.left,entry.top,entry.right,entry.bottom,entry.sample);}
    private static float[] atlas(Context context,int resource,float start,float end) {
        return atlas(context,resource,start,0,end,1,2);
    }
    private static float[] atlas(Context context,int resource,float startX,float startY,float endX,float endY,int sample) {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;
        Bitmap atlas=BitmapFactory.decodeResource(context.getResources(),resource,options);
        if(atlas==null)throw new IllegalStateException("Missing built-in image atlas");
        try {
            int left=Math.round(atlas.getWidth()*startX),right=Math.round(atlas.getWidth()*endX);
            int top=Math.round(atlas.getHeight()*startY),bottom=Math.round(atlas.getHeight()*endY);
            Bitmap image=Bitmap.createBitmap(atlas,left,top,right-left,bottom-top);
            try {return sample(image,true);} finally {image.recycle();}
        } finally {atlas.recycle();}
    }

    /** Sample a luminance-weighted cloud; image backgrounds are reduced by a simple edge term. */
    private static float[] sample(Bitmap bitmap, boolean image) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        Random random = new Random(71);
        List<Integer> candidates = new ArrayList<>();
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            if (x==1 && Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
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
        float[] result = new float[(image ? TARGET_POINTS : TEXT_TARGET_POINTS) * 4];
        float scale = .96f / Math.max(w, h);
        for (int i = 0; i < result.length; i += 4) {
            if ((i & 4095)==0 && Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
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
