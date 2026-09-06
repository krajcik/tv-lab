package ru.krajcik.tvlab.particles;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

final class ImageLibrary {
    static final class Entry {
        final String id,title,category;
        final int resource,sample;
        final float left,top,right,bottom;
        Entry(JSONObject item) throws org.json.JSONException {
            id=item.getString("id");title=item.getString("title");category=item.getString("category");
            resource=resource(item.getString("resource"));sample=item.getInt("sample");
            JSONArray r=item.getJSONArray("crop");left=(float)r.getDouble(0);top=(float)r.getDouble(1);right=(float)r.getDouble(2);bottom=(float)r.getDouble(3);
            if(!(left>=0&&top>=0&&right<=1&&bottom<=1&&right>left&&bottom>top)||(sample!=1&&sample!=2))throw new IllegalArgumentException("Invalid image bounds");
        }
    }
    static List<Entry> read(Context context){
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.images),StandardCharsets.UTF_8))){
            StringBuilder text=new StringBuilder();String line;while((line=reader.readLine())!=null)text.append(line).append('\n');
            JSONArray data=new JSONArray(text.toString());List<Entry> result=new ArrayList<>();HashSet<String> ids=new HashSet<>();
            for(int i=0;i<data.length();i++){Entry entry=new Entry(data.getJSONObject(i));if(!ids.add(entry.id))throw new IllegalArgumentException("Duplicate image id");result.add(entry);}
            return result;
        }catch(java.io.IOException|org.json.JSONException error){throw new IllegalStateException("Invalid image library",error);}
    }
    private static int resource(String name){
        switch(name){
            case "pets":return R.drawable.pets;
            case "pet_portraits":return R.drawable.pet_portraits;
            case "nature":return R.drawable.nature;
            case "space":return R.drawable.space;
            case "library_01":return R.drawable.library_01;
            case "library_02":return R.drawable.library_02;
            case "library_03":return R.drawable.library_03;
            case "library_04":return R.drawable.library_04;
            case "library_05":return R.drawable.library_05;
            case "library_06":return R.drawable.library_06;
            case "library_07":return R.drawable.library_07;
            case "library_08":return R.drawable.library_08;
            case "library_09":return R.drawable.library_09;
            case "library_10":return R.drawable.library_10;
            default:throw new IllegalArgumentException("Unknown image atlas: "+name);
        }
    }
}
