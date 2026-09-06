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
    private boolean fontPreviews,imageInventory,clarityPreviews,modePreviews;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments);modePreviews=arguments!=null&&"true".equals(arguments.getString("modePreviews"));clarityPreviews=arguments!=null&&"true".equals(arguments.getString("clarityPreviews"));fontPreviews=arguments!=null&&"true".equals(arguments.getString("fontPreviews"));imageInventory=arguments!=null&&"true".equals(arguments.getString("imageInventory"));start(); }

    @Override public void onStart() {
        if(modePreviews){
            Bundle result=new Bundle();Activity preview=null;
            try{preview=startActivitySync(new Intent(getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));result.putString("stream",GpuModeTest.run(this,preview));finish(Activity.RESULT_OK,result);}
            catch(Throwable error){result.putString("stream",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}
            finally{if(preview!=null){Activity current=preview;runOnMainSync(current::finish);}}
            return;
        }
        if(clarityPreviews){
            Bundle result=new Bundle();Activity preview=null;
            try{preview=startActivitySync(new Intent(getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));result.putString("stream","PASS: clarity GPU captures\n"+ClarityPreviews.write(this,preview));finish(Activity.RESULT_OK,result);}
            catch(Exception|AssertionError error){result.putString("stream",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}
            finally{if(preview!=null){Activity current=preview;runOnMainSync(current::finish);}}
            return;
        }
        if(imageInventory){
            Bundle result=new Bundle();
            try{ImageInventory.verify(getTargetContext());result.putString("stream","PASS: all100 actual image entries decoded into distinct nonempty clouds\n");finish(Activity.RESULT_OK,result);}
            catch(Exception|AssertionError error){result.putString("stream",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}
            return;
        }
        if(fontPreviews){
            Bundle result=new Bundle();
            try{FontPreviews.write(getTargetContext());result.putString("stream","PASS: three native Cyrillic font previews generated\n");finish(Activity.RESULT_OK,result);}
            catch(Exception error){result.putString("stream",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}
            return;
        }
        Bundle result = new Bundle();
        Activity activity = null;
        File source = new File(getTargetContext().getCacheDir(), "orientation-test.jpg");
        File output = new File(getTargetContext().getFilesDir(), DreamConfig.IMAGE_FILE);
        try {
            DreamConfig.preferences(getTargetContext()).edit().clear().commit();
            Files.deleteIfExists(output.toPath());
            DreamConfig live=new DreamConfig(getTargetContext());
            require(live.procedural&&live.modes==255&&live.modeSeconds==3600,"Meditation defaults: eight modes, one hour, no gallery");
            ScenePlaylist liveScenes=SceneFactory.playlist(getTargetContext(),live);
            require(liveScenes.size()==8,"Only eight procedural scenes in meditation");
            for(int id:liveScenes.modes)require(id>=1&&id<=8,"No gallery scenes in meditation");
            DreamConfig.preferences(getTargetContext()).edit().putInt("mode_seconds",0).commit();
            require(SceneFactory.playlist(getTargetContext(),new DreamConfig(getTargetContext())).size()==1,"Endless mode selects exactly one scene");
            DreamConfig.preferences(getTargetContext()).edit().putBoolean("procedural",false).putInt("mode_seconds",3600).commit();
            DreamConfig defaults = new DreamConfig(getTargetContext());
            require(!defaults.text && defaults.pictures && defaults.quotes, "Defaults must show the image and quote libraries");
            java.util.List<QuoteLibrary.Quote> quotes=QuoteLibrary.read(getTargetContext());
            require(quotes.size()==1000,"Curated library must contain 1000 verified stoic quotations");
            android.graphics.Paint paint=new android.graphics.Paint();paint.setTextSize(48);
            for(QuoteLibrary.Quote quote:quotes){
                require(java.util.Arrays.asList("Сенека","Марк Аврелий","Эпиктет").contains(quote.author)&&!quote.work.isEmpty()&&quote.source.startsWith("https://"),"Each quote needs attribution and a source");
                for(String line:SceneFactory.wrap(quote.text,paint,576))require(paint.measureText(line)<=576.1f,"Quote line must fit the screen");
            }
            for(String line:SceneFactory.wrap(new String(new char[100]).replace("\0","W"),paint,576))require(paint.measureText(line)<=576.1f,"Unbroken custom words must wrap");
            java.util.List<String> wrapped=SceneFactory.wrap("Спокойная мысль помогает увидеть происходящее яснее и не спешить с суждением",paint,576);
            require(android.text.TextUtils.join(" ",wrapped).equals("Спокойная мысль помогает увидеть происходящее яснее и не спешить с суждением"),"Natural phrases must wrap at word boundaries");
            int defaultCount=2*Math.max(SceneFactory.BUILTIN_IMAGES,quotes.size());
            int uniqueCount=SceneFactory.BUILTIN_IMAGES+quotes.size();
            ScenePlaylist catalogue=SceneFactory.playlist(getTargetContext(),defaults);
            require(catalogue.size()==defaultCount,"Default playlist must contain the full alternating catalogue");
            java.util.Set<SceneSource> recipes=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            java.util.Set<Integer> unique=new java.util.HashSet<>();
            for(SceneSource recipe:catalogue.sources)if(recipes.add(recipe)){
                float[] cloud=recipe.load();ScenePlaylist.validate(cloud);
                require(cloud.length>=1000,"Each asset must produce a nonempty cloud");unique.add(Arrays.hashCode(cloud));
            }
            require(recipes.size()==uniqueCount&&unique.size()==uniqueCount,"All1100 unique scene recipes must materialize correctly");
            try(SceneCache cache=new SceneCache(catalogue)){
                cache.prepareInitial();long deadline=System.nanoTime()+5_000_000_000L;
                while(cache.peek(2)==null&&System.nanoTime()<deadline)Thread.sleep(5);
                require(cache.peek(2)!=null,"Real asset prefetch should complete");
                require(cache.residentCount()<=3&&cache.residentBytes()<=3L*1024*1024,"Real Android cache must remain bounded");
            }

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
            require(uniqueRecipes(SceneFactory.playlist(getTargetContext(), defaults)) == 1+quotes.size(), "A custom image must replace all standard image recipes");
            Files.write(source.toPath(), new byte[] {1, 2, 3});
            try { importer.invoke(activity, Uri.fromFile(source)); throw new AssertionError("Invalid image accepted"); }
            catch (java.lang.reflect.InvocationTargetException expected) {
                require(expected.getCause() instanceof java.io.IOException, "Invalid image must fail as a decode error");
            }
            require(output.isFile(), "A failed import must preserve the previous image");
            Files.deleteIfExists(output.toPath());
            require(uniqueRecipes(SceneFactory.playlist(getTargetContext(), defaults)) == uniqueCount, "Removing custom image must restore all standard scenes");
            String gpuResult=GpuParityTest.run(this,activity);
            result.putString("stream", "PASS: default animals, settings launch, EXIF rotation, original preserved, custom replacement, failed import recovery, default restoration; 100 images, 1000 attributed stoic quotes, wrapping, lazy bounded cache; "+gpuResult+"\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            if (activity != null) { Activity current = activity; runOnMainSync(current::finish); }
            source.delete(); output.delete();
        }
    }

    private static int uniqueRecipes(ScenePlaylist playlist){java.util.Set<SceneSource> unique=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());java.util.Collections.addAll(unique,playlist.sources);return unique.size();}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
