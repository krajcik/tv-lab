package ru.krajcik.tvlab.particles;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.io.File;
import java.io.FileOutputStream;

final class FontPreviews {
    static void write(Context context) throws java.io.IOException {
        String text="Не желай, чтобы происходящее происходило по твоему желанию; желай, чтобы оно происходило как происходит.";
        for(String family:DreamConfig.FONTS){
            Bitmap mask=SceneFactory.renderText(text,"Эпиктет",family);
            Bitmap preview=Bitmap.createBitmap(mask.getWidth(),mask.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(preview);canvas.drawColor(Color.BLACK);canvas.drawBitmap(mask,0,0,null);
            Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);label.setColor(0xff78948f);label.setTextSize(14);
            canvas.drawText(family+" · native Android text mask",20,25,label);
            try(FileOutputStream out=new FileOutputStream(new File(context.getCacheDir(),"font-"+family+".png"))){preview.compress(Bitmap.CompressFormat.PNG,100,out);}
            mask.recycle();preview.recycle();
        }
    }
}
