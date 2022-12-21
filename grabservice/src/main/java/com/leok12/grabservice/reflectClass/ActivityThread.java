package com.leok12.grabservice.reflectClass;

import android.content.Context;

import com.leok12.grabservice.reflectUtils.MethodUtils;

import java.lang.reflect.InvocationTargetException;

public class ActivityThread {
    private static Object mActivityThread = null;
    private static final Object newInstance() {
        if (mActivityThread != null) {
            return mActivityThread;
        }

        try {
            Class<?> clsActivityThread = Class.forName("android.app.ActivityThread");
            mActivityThread = MethodUtils.invokeConstructor(clsActivityThread, null);
        }catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }  catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        return mActivityThread;
    }

    public static boolean attach(){
        boolean ret = false;
        try {
            Object activityThread = newInstance();
            if (activityThread == null){
                return ret;
            }

            MethodUtils.invokeMethod(activityThread, "attach", null);
            ret = true;
        } catch (Throwable e){
            e.printStackTrace();
        }

        return ret;
    }

    public static Context currentApplication(){
        Context context = null;
        try {
            Object activityThread = newInstance();
            if (activityThread == null){
                return context;
            }

            context = (Context) MethodUtils.invokeMethod(activityThread, "currentApplication", null);
        } catch (Throwable e){
            e.printStackTrace();
        }

        return context;
    }
}
