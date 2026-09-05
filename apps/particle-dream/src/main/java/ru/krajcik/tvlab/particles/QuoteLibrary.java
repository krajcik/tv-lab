package ru.krajcik.tvlab.particles;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class QuoteLibrary {
    static final class Quote {
        final String text, author, work, source, translation;
        Quote(JSONObject item) throws org.json.JSONException {
            text=item.getString("text"); author=item.getString("author");
            work=item.getString("work"); source=item.getString("source");
            translation=item.getString("translation");
        }
    }
    static List<Quote> read(Context context) {
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                context.getResources().openRawResource(R.raw.quotes),StandardCharsets.UTF_8))){
            StringBuilder data=new StringBuilder();String line;
            while((line=reader.readLine())!=null)data.append(line).append('\n');
            JSONArray array=new JSONArray(data.toString());
            List<Quote> result=new ArrayList<>();
            for(int i=0;i<array.length();i++)result.add(new Quote(array.getJSONObject(i)));
            return result;
        }catch(java.io.IOException|org.json.JSONException error){throw new IllegalStateException("Invalid built-in quote library",error);}
    }
}
