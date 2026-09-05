package ru.krajcik.tvlab.particles;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;

/** Run only on a disposable emulator; this resets its app preferences and imported test image. */
public final class SmokeInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        Activity activity = null;
        File source = new File(getTargetContext().getCacheDir(), "orientation-test.jpg");
        File output = new File(getTargetContext().getFilesDir(), DreamConfig.IMAGE_FILE);
        try {
            DreamConfig.preferences(getTargetContext()).edit().clear().commit();
            Files.deleteIfExists(output.toPath());
            DreamConfig defaults = new DreamConfig(getTargetContext());
            require(!defaults.text && defaults.pictures, "Defaults must show animals, not phrases");
            float[][] scenes = SceneFactory.create(getTargetContext(), defaults);
            require(scenes.length == 3, "Default playlist must have three animals");
            for (float[] scene : scenes) require(scene.length >= 1000, "Animal must have a nonempty point cloud");
            require(!Arrays.equals(scenes[0], scenes[1]) && !Arrays.equals(scenes[1], scenes[2]), "Animal scenes must differ");

            activity = startActivitySync(new Intent(getTargetContext(), SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            require(activity.findViewById(R.id.phrases) != null, "Settings must create the phrase editor");
            Bitmap fixture = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888);
            fixture.eraseColor(Color.WHITE);
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            fixture.compress(Bitmap.CompressFormat.JPEG, 95, encoded); fixture.recycle();
            byte[] jpeg = encoded.toByteArray();
            // APP1 EXIF: big-endian TIFF, one SHORT orientation tag = 6 (90 degrees CW).
            byte[] exif = {(byte)0xff,(byte)0xe1,0,34,69,120,105,102,0,0,77,77,0,42,0,0,0,8,
                    0,1,1,18,0,3,0,0,0,1,0,6,0,0,0,0,0,0};
            ByteArrayOutputStream rotated = new ByteArrayOutputStream();
            rotated.write(jpeg, 0, 2); rotated.write(exif); rotated.write(jpeg, 2, jpeg.length - 2);
            byte[] original = rotated.toByteArray();
            Files.write(source.toPath(), original);
            Method importer = SettingsActivity.class.getDeclaredMethod("importImage", Uri.class);
            importer.setAccessible(true);
            importer.invoke(activity, Uri.fromFile(source));
            Bitmap imported = BitmapFactory.decodeFile(output.getAbsolutePath());
            require(imported != null && imported.getWidth() == 40 && imported.getHeight() == 80, "Import must apply EXIF rotation");
            imported.recycle();
            require(Arrays.equals(original, Files.readAllBytes(source.toPath())), "Import must preserve the original file");
            require(SceneFactory.create(getTargetContext(), defaults).length == 1, "A custom image must replace the default animals");
            Files.write(source.toPath(), new byte[] {1, 2, 3});
            try { importer.invoke(activity, Uri.fromFile(source)); throw new AssertionError("Invalid image accepted"); }
            catch (java.lang.reflect.InvocationTargetException expected) {
                require(expected.getCause() instanceof java.io.IOException, "Invalid image must fail as a decode error");
            }
            require(output.isFile(), "A failed import must preserve the previous image");
            Files.deleteIfExists(output.toPath());
            require(SceneFactory.create(getTargetContext(), defaults).length == 3, "Removing custom image must restore animals");
            result.putString("stream", "PASS: default animals, settings launch, EXIF rotation, original preserved, custom replacement, failed import recovery, default restoration\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            if (activity != null) { Activity current = activity; runOnMainSync(current::finish); }
            source.delete(); output.delete();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
