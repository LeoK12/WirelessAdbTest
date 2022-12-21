package com.leok12.wirelessadbtest;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

public class ShellService extends Service {
    private static final String TAG = ShellService.class.getSimpleName();
    public static final String START_ACTION = "start";
    public static final String STOP_ACTION = "stop";
    public static final String EXTRA_HOST = "host";
    public static final String EXTRA_PORT = "port";
    private Queue<CmdInfo> mCmdQueue = new LinkedBlockingDeque<>();
    private AdbClient mAdbClient;
    private RunShellThread mRunShellThread;
    private AdbMDNS mAdbMDNS;
    private Handler mHandler;

    public ShellService(){
        mAdbClient = null;
        mRunShellThread = new RunShellThread();
        mRunShellThread.setName("RunShellThread");
        mRunShellThread.start();
    }

    private void enqueueCmdInfo(CmdInfo info){
        if (info == null){
            return;
        }

        synchronized (mCmdQueue){
            try {
                mCmdQueue.add(info);
            } catch (Exception e){
                e.printStackTrace();
            }

            mCmdQueue.notify();
        }
    }

    private void handleStart(Intent intent){
        String host = intent.getStringExtra(EXTRA_HOST);
        int port = intent.getIntExtra(EXTRA_PORT, -1);
        if (port == -1){
            Log.e(TAG, "port should not be -1");
        }

        mAdbMDNS.setPortListener(null);
        mAdbMDNS.stop();

        mAdbMDNS.setPortListener(new ConnectPortListener(mHandler, host, port));
        mAdbMDNS.start();
    }

    private void handleStopInternal(){
        mAdbMDNS.setPortListener(null);
        mAdbMDNS.stop();
    }

    private void handleStop(){
        handleStopInternal();
        CmdInfo info = new CmdInfo(CmdInfo.TYPE_STOP, null, -1, null);
        enqueueCmdInfo(info);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mAdbMDNS = new AdbMDNS(getApplicationContext(), AdbMDNS.ADB_MDNS_TLS_CONNECT_TYPE);
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        switch (action){
            case START_ACTION:
                handleStart(intent);
                break;
            case STOP_ACTION:
                handleStop();
                break;
            default:
                break;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class CmdInfo{
        public static final int TYPE_START = 0;
        public static final int TYPE_STOP = 1;

        private int type;
        private String host;
        private int port;
        private String cmd;

        public CmdInfo(int type, String host, int port, String cmd){
            this.type = type;
            this.host = host;
            this.port = port;
            this.cmd = cmd;
        }

        public int getPort() {
            return port;
        }

        public String getHost() {
            return host;
        }

        public String getCmd() {
            return cmd;
        }

        public int getType() {
            return type;
        }
    }

    private class RunShellThread extends Thread{

        private void handleStop(){
            if (mAdbClient != null) {
                mAdbClient.close();
                mAdbClient = null;
            }
        }

        private void handleStart(CmdInfo info){
            if (mAdbClient == null) {
                AdbKey adbKey = new AdbKey(
                        PreferenceAdbKeyStore.getPreferenceAdbKeyStore(getApplicationContext()),
                        getPackageName());
                if (!adbKey.init()) {
                    Log.e(TAG, "failed to call init of adbKey");
                    return;
                }

                AdbClient adbClient = new AdbClient(info.getHost(), info.getPort(), adbKey);
                if (adbClient.connect()) {
                    Log.e(TAG, "failed to connect");
                    return;
                }

                mAdbClient = adbClient;
            }

            String scriptPath = info.getCmd();
            if (scriptPath == null) {
                scriptPath = "/data/local/tmp/startGrabService.sh";
            }

            Log.d(TAG, "scriptPath = " + scriptPath);
            String cmd = String.format("sh %s", scriptPath);
            if (!mAdbClient.run(cmd)){
                Log.e(TAG, "failed to run " + cmd);
                handleStop();
                return;
            }
        }

        private void processCmd(CmdInfo info){
            if (info.getType() == CmdInfo.TYPE_STOP){
                handleStop();
                return;
            }

            handleStart(info);
        }

        @Override
        public void run() {
            while (true){
                CmdInfo info;
                synchronized (mCmdQueue){
                    info = mCmdQueue.poll();
                    if (info == null){
                        try {
                            mCmdQueue.wait();
                        } catch (Exception e){
                            e.printStackTrace();
                        }

                        info = mCmdQueue.poll();
                    }
                }

                if (info == null){
                    continue;
                }

                processCmd(info);
                handleStopInternal();
            }
        }
    }

    class ConnectPortListener implements AdbMDNS.PortListener{
        private Handler mHandler;
        private String mHost;
        private Integer mPort;
        ConnectPortListener(Handler handler, String host, int port){
            mHandler = handler;
            mHost = host;
            mPort = port;
        }

        @Override
        public void setPort(int port) {
            synchronized (mPort) {
                mPort = port;
            }

            mHandler.post(() -> {
                CmdInfo info = new CmdInfo(CmdInfo.TYPE_START, mHost, mPort, null);
                enqueueCmdInfo(info);
            });
        }
    }
}