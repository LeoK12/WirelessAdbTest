package com.leok12.grabservice.reflectClass;

import android.view.InputEvent;
import com.leok12.grabservice.reflectUtils.MethodUtils;

public class InputManager {
    private final static String TAG = "InputManager";
    public static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;
    public static final int INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT = 1;
    public static final int INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 2;

    public static Object getInputManager() {
        try {
            Class clsInputManager = Class.forName("android.hardware.input.InputManager");
            return MethodUtils.invokeStaticMethod(clsInputManager, "getInstance");
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    public static boolean injectInputEvent(InputEvent event, int mode){
        boolean ret = false;
        try {
            Object inputManager = getInputManager();
            if (inputManager == null){
                return ret;
            }

            ret = (boolean) MethodUtils.invokeMethod(inputManager, "injectInputEvent", new Object[]{event, mode});
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return ret;
    }
}
