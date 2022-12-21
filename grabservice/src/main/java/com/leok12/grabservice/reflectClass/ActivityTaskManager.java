package com.leok12.grabservice.reflectClass;

import com.leok12.grabservice.reflectUtils.MethodUtils;

import android.app.ActivityManager;
import android.app.TaskStackListener;
import android.os.Build;

import java.util.List;

public class ActivityTaskManager {
    public static Object getIActivityTaskManager() {
        try {
            Class clsInputManager = Class.forName("android.app.ActivityTaskManager");
            return MethodUtils.invokeStaticMethod(clsInputManager, "getService");
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    public static Object getActivityTaskManager(){
        try {
            Class clsInputManager = Class.forName("android.app.ActivityTaskManager");
            return MethodUtils.invokeStaticMethod(clsInputManager, "getInstance");
        } catch (Throwable e){
            e.printStackTrace();
        }

        return null;
    }

    public static void registerTaskStackListener(TaskStackListener listener){
        try {
            Object iActivityTaskManager = getIActivityTaskManager();
            if (iActivityTaskManager == null){
                return;
            }

            MethodUtils.invokeMethod(iActivityTaskManager, "registerTaskStackListener",
                    new Object[]{listener});
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static List<ActivityManager.RunningTaskInfo> getTasks(int maxNum){
        List<ActivityManager.RunningTaskInfo> taskInfoList = null;
        try {
            if (Build.VERSION.SDK_INT < 31) {
                Object iActivityTaskManager = getIActivityTaskManager();
                if (iActivityTaskManager == null){
                    return taskInfoList;
                }

                taskInfoList = (List<ActivityManager.RunningTaskInfo>) MethodUtils.invokeMethod(iActivityTaskManager, "getTasks",
                        new Object[]{maxNum});
            } else {
                Object activityTaskManager = getActivityTaskManager();
                if (activityTaskManager == null){
                    return taskInfoList;
                }

                taskInfoList = (List<ActivityManager.RunningTaskInfo>) MethodUtils.invokeMethod(activityTaskManager, "getTasks",
                        new Object[]{maxNum});
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return taskInfoList;
    }
}
