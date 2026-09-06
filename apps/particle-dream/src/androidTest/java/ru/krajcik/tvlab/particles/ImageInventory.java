package ru.krajcik.tvlab.particles;

import android.content.Context;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

final class ImageInventory {
    static void verify(Context context){
        List<ImageLibrary.Entry> entries=ImageLibrary.read(context);
        if(entries.size()!=100)throw new AssertionError("Expected100 actual image entries");
        HashSet<String> ids=new HashSet<>();HashSet<Integer> clouds=new HashSet<>();
        for(ImageLibrary.Entry entry:entries){
            if(entry.title.isEmpty()||!ids.add(entry.id))throw new AssertionError("Invalid image identity");
            float[] data=SceneFactory.image(context,entry);ScenePlaylist.validate(data);
            if(data.length!=SceneFactory.TARGET_POINTS*4)throw new AssertionError("Incorrect cloud size: "+entry.id);
            if(!clouds.add(Arrays.hashCode(data)))throw new AssertionError("Duplicate image cloud: "+entry.id);
            float xmin=1,xmax=-1,ymin=1,ymax=-1;
            for(int i=0;i<data.length;i+=4){xmin=Math.min(xmin,data[i]);xmax=Math.max(xmax,data[i]);ymin=Math.min(ymin,data[i+1]);ymax=Math.max(ymax,data[i+1]);}
            if(xmax-xmin<.05f||ymax-ymin<.01f)throw new AssertionError("Image collapsed to an empty/sliver cloud: "+entry.id);
        }
    }
}
