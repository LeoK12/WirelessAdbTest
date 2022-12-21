package com.leok12.grabservice.reflectClass;

import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.view.DisplayInfo;

import com.leok12.grabservice.reflectUtils.MethodUtils;

public class DisplayManagerGlobal {

    public static Object getDisplayManager() {
        try {
            Class clsInputManager = Class.forName("android.hardware.display.DisplayManagerGlobal");
            return MethodUtils.invokeStaticMethod(clsInputManager, "getInstance");
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    public static void registerDisplayListener(DisplayManager.DisplayListener listener, Handler handler){
        try {
            Object displayManager = getDisplayManager();
            if (displayManager == null){
                return;
            }

            if (Build.VERSION.SDK_INT < 31) {
                MethodUtils.invokeMethod(displayManager, "registerDisplayListener",
                        new Object[]{listener, handler},
                        new Class<?>[]{DisplayManager.DisplayListener.class, Handler.class});
            } else {
                final long EVENT_FLAG_DISPLAY_ADDED = 1L << 0;
                final long EVENT_FLAG_DISPLAY_REMOVED = 1L << 1;
                final long EVENT_FLAG_DISPLAY_CHANGED = 1L << 2;
                MethodUtils.invokeMethod(displayManager, "registerDisplayListener",
                        new Object[]{listener, handler, EVENT_FLAG_DISPLAY_ADDED|EVENT_FLAG_DISPLAY_REMOVED|EVENT_FLAG_DISPLAY_CHANGED},
                        new Class<?>[]{DisplayManager.DisplayListener.class, Handler.class, long.class});
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static DisplayInfo getDisplayInfo(int displayId){
        DisplayInfo info = null;
        try {
            Object displayManager = getDisplayManager();
            if (displayManager == null){
                return info;
            }

            info = (DisplayInfo)MethodUtils.invokeMethod(displayManager, "getDisplayInfo", new Object[]{displayId});
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return info;
    }
}
