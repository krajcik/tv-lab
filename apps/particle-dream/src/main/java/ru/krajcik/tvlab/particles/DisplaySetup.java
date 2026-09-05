package ru.krajcik.tvlab.particles;

import android.content.Context;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;
import android.util.Log;

final class DisplaySetup {
    static void request4k60(Window window) {
        WindowManager manager=(WindowManager)window.getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display=manager.getDefaultDisplay();
        Display.Mode selected=null;
        for(Display.Mode mode:display.getSupportedModes()){
            if(mode.getPhysicalWidth()!=3840||mode.getPhysicalHeight()!=2160)continue;
            if(selected==null||Math.abs(mode.getRefreshRate()-60)<Math.abs(selected.getRefreshRate()-60))selected=mode;
        }
        WindowManager.LayoutParams params=window.getAttributes();
        params.preferredRefreshRate=60;
        if(selected!=null)params.preferredDisplayModeId=selected.getModeId();
        window.setAttributes(params);
        Log.i("ParticlesPerf",selected==null?"No physical 4K display mode; rendering a 4K surface for this display"
                :"Requested physical mode="+selected);
    }
}
