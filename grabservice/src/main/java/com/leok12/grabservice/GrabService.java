package com.leok12.grabservice;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.TaskStackListener;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;
import android.view.DisplayInfo;

import com.leok12.grabservice.reflectClass.DisplayManagerGlobal;
import com.leok12.grabservice.reflectClass.ActivityTaskManager;

import java.util.List;

public class GrabService {
    private static final String TAG = GrabService.class.getSimpleName();
    private InjectThread mThread;
    private HandlerThread mDisplayHandlerThread;
    private DisplayInfo mDisplayInfo;
    private TaskStackListener mTaskStackListener;
    private String mLastPackageName;

    private static native int nativeInit(GrabService service);
    private static native void nativeSetDisplayInfo(int width, int height, int rotation);
    private static native void nativeUpdateTopPackageName(String packageName);

    public boolean loadLocalNativeLibrary(){
        try {
            System.load("/data/local/tmp/libgrabservice.so");
            return true;
        } catch (UnsatisfiedLinkError e){
            e.printStackTrace();
        } catch (Exception ee){
            ee.printStackTrace();
        }

        return false;
    }

    private DisplayInfo getDisplayInfo(){
       return DisplayManagerGlobal.getDisplayInfo(Display.DEFAULT_DISPLAY);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void initDisplayRotation(){
        mDisplayInfo = getDisplayInfo();
        nativeSetDisplayInfo(mDisplayInfo.logicalWidth, mDisplayInfo.logicalHeight, mDisplayInfo.rotation);
        DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {

            }

            @Override
            public void onDisplayRemoved(int displayId) {

            }

            @Override
            public void onDisplayChanged(int displayId) {
                DisplayInfo info = getDisplayInfo();
                if (mDisplayInfo.logicalWidth != info.logicalWidth ||
                    mDisplayInfo.logicalHeight != info.logicalHeight ||
                    mDisplayInfo.rotation != info.rotation){
                    mDisplayInfo = info;
//                    nativeSetDisplayInfo(mDisplayInfo.logicalWidth, mDisplayInfo.logicalHeight, mDisplayInfo.rotation);
                }

                Log.e(TAG, "mDisplayInfo = " + mDisplayInfo);
            }
        };

        mDisplayHandlerThread = new HandlerThread("DisplayRotationListenerThread");
        mDisplayHandlerThread.start();
        DisplayManagerGlobal.registerDisplayListener(listener, new Handler(mDisplayHandlerThread.getLooper()));
    }

    private String getTopTaskPackageName(){
        String packageName = null;
        List<ActivityManager.RunningTaskInfo> topTasks = ActivityTaskManager.getTasks(1);
        if (!topTasks.isEmpty()) {
            packageName = topTasks.get(0).topActivity.getPackageName();
            if (mLastPackageName == null){
                mLastPackageName = packageName;
            } else {
                if (mLastPackageName.equalsIgnoreCase(packageName)){
                    return packageName;
                }

                mLastPackageName = packageName;
            }
            Log.e(TAG, "topTask = " + packageName);
            nativeUpdateTopPackageName(packageName);
        }

        return packageName;
    }

    private void initTaskStackListner(){
        getTopTaskPackageName();
        mTaskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() throws RemoteException {
                getTopTaskPackageName();
            }
        };

        ActivityTaskManager.registerTaskStackListener(mTaskStackListener);
    }


    public GrabService(){
        mLastPackageName = null;
    }

    private boolean init(){
        if (!loadLocalNativeLibrary()){
            return false;
        }

        initDisplayRotation();
        initTaskStackListner();

        mThread = new InjectThread();
        mThread.start();

        return true;
    }

    public boolean start(){
        if (!init()){
            return false;
        }

        String shellStartApp = "am start com.leok12.grabclientnative/.MainActivity";
        if (shellStartApp != null) {
            try {
                Runtime.getRuntime().exec(String.format(shellStartApp));
            } catch (Exception e){
                e.printStackTrace();
            }
        }

        return nativeInit(this) < 0;
    }

    private void interceptData(GrabMotionData data){
        Log.e(TAG, "data = " + data);
        mThread.queue(data);
    }

    private void interceptData(GrabKeyData data){
        Log.e(TAG, "data = " + data);
        mThread.queue(data);
    }

    public static void main(String[] args) {
        GrabService service = new GrabService();
        if (!service.start()){
            Log.e(TAG, "failed to start native process");
        }
    }
}
