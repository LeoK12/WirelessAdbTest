package com.leok12.wirelessadbtest;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.Service;
import android.content.Intent;
import android.net.Network;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;

@TargetApi(Build.VERSION_CODES.R)
public class PairService extends Service {
    private static final String TAG = PairService.class.getSimpleName();
    public static final int FLAG_MUTABLE = 1<<25;
    public static final String NOTIFICATION_CHANNEL_PAIR = "pair";
    public static final String START_ACTION = "start";
    public static final String REPLY_ACTION = "reply";
    public static final String STOP_ACTION = "stop";
    private static final String REMOTE_INPUT_RESULT_KEY = "pair_code";

    private static final int notificationId = 1;
    private static final int replyRequestId = 1;
    private static final int stopRequestId = 2;
    private static final int shellRequestId = 3;

    private NotificationManager mNotificationManager;
    private AdbMDNS mAdbMDNS;
    private Integer mPort;

    private Handler mHandler;

    private Notification mSearchNotification;
    private Notification mInputNotification;
    private Notification mWorkingNotification;

    public PairService() {
        mPort = PairPortListener.UNAVAILABLE_PORT;
    }

    private Notification.Action getShellNotificationAction(){
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R){
            flags |= FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), shellRequestId, intent, flags);

        return new Notification.Action.Builder(
                null,getString(R.string.start_shell_title), pendingIntent)
                .build();
    }

    private Notification.Action getStopNotificationAction(){
        Intent intent = new Intent(getApplicationContext(), PairService.class);
        intent.setAction(STOP_ACTION);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R){
            flags |= FLAG_MUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getForegroundService(this, stopRequestId, intent, flags);

        return new Notification.Action.Builder(
                null,getString(R.string.stop_search_tile), pendingIntent)
                .build();
    }

    private Notification.Action getReplyNotificationAction(){
        RemoteInput remoteInput = new RemoteInput.Builder(REMOTE_INPUT_RESULT_KEY)
                .setLabel(getString(R.string.pair_code_label)).build();

        Intent intent = new Intent(getApplicationContext(), PairService.class);
        intent.setAction(REPLY_ACTION);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R){
            flags |= FLAG_MUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getForegroundService(this, replyRequestId, intent, flags);

        return new Notification.Action.Builder(
                null,getString(R.string.enter_pair_code_title), pendingIntent)
                .addRemoteInput(remoteInput)
                .build();
    }

    private void createNotificationChannel(){
        NotificationChannel notificationChannel =
                new NotificationChannel(NOTIFICATION_CHANNEL_PAIR, getString(R.string.adb_pair),
                        NotificationManager.IMPORTANCE_HIGH);
        notificationChannel.setSound(null, null);
        notificationChannel.setShowBadge(false);
        notificationChannel.setAllowBubbles(false);
        mNotificationManager.createNotificationChannel(notificationChannel);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = getSystemService(NotificationManager.class);
        createNotificationChannel();

        mAdbMDNS = new AdbMDNS(getApplicationContext(), AdbMDNS.ADB_MDNS_TLS_PAIRING_TYPE);
        mHandler = new Handler(Looper.getMainLooper());
        mSearchNotification = new Notification.Builder(this, NOTIFICATION_CHANNEL_PAIR)
                .setColor(getColor(R.color.notification))
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle(getString(R.string.search_notification_title))
                .addAction(getStopNotificationAction())
                .build();
        mInputNotification = new Notification.Builder(this, NOTIFICATION_CHANNEL_PAIR)
                .setColor(getColor(R.color.notification))
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle(getString(R.string.input_notification_title))
                .addAction(getReplyNotificationAction())
                .build();
        mWorkingNotification = new Notification.Builder(this, NOTIFICATION_CHANNEL_PAIR)
                .setColor(getColor(R.color.notification))
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle(getString(R.string.work_notification_title))
                .build();
        Log.d(TAG, "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        switch (action){
            case START_ACTION:
                handleStart();
                break;
            case REPLY_ACTION:
                handleReply(intent);
                break;
            case STOP_ACTION:
                handleStop();
            default:
                break;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void startSearch(){
        stopSearch();

        mAdbMDNS.setPortListener(new PairPortListener(mHandler));
        mAdbMDNS.start();
    }

    private void stopSearch(){
        mAdbMDNS.setPortListener(null);
        mAdbMDNS.stop();
    }

    private void handleStart(){
        Log.d(TAG, "handleStart");
        startSearch();
        startForeground(notificationId, getNotificationByPort());
    }

    private void handleReply(Intent intent){
        new Thread(){
            private void notifyNotification(String title, String context){
                stopForeground(false);
                if (title.equals("success")){
                    mNotificationManager.notify(
                            notificationId,
                            new Notification.Builder(getApplicationContext(), NOTIFICATION_CHANNEL_PAIR)
                                    .setColor(getColor(R.color.notification))
                                    .setSmallIcon(R.drawable.ic_launcher_background)
                                    .setContentTitle(title)
                                    .addAction(getShellNotificationAction())
                                    .build());
                } else {
                    mNotificationManager.notify(
                            notificationId,
                            new Notification.Builder(getApplicationContext(), NOTIFICATION_CHANNEL_PAIR)
                                    .setColor(getColor(R.color.notification))
                                    .setSmallIcon(R.drawable.ic_launcher_background)
                                    .setContentTitle(title)
                                    .setContentText(context)
                                    .build());
                }
            }

            @Override
            public void run() {
                String title = "failed";
                String context = "failed to get result form intent";
                Bundle resultsFromIntent = RemoteInput.getResultsFromIntent(intent);
                if (resultsFromIntent == null){
                    notifyNotification(title, context);
                    return;
                }

                CharSequence code = resultsFromIntent.getCharSequence(REMOTE_INPUT_RESULT_KEY);
                Log.d(TAG, "handleReply : code = " + code.toString());
                AdbKey adbKey = new AdbKey(
                        PreferenceAdbKeyStore.getPreferenceAdbKeyStore(getApplicationContext()),
                        getPackageName());
                if (!adbKey.init()){
                    context = "failed to call init of adbKey";
                    notifyNotification(title, context);
                    return;
                }

                AdbPair adbPair = new AdbPair(AdbMDNS.LOCALHOST, mPort, code.toString(), adbKey);
                if (!adbPair.start()){
                    Log.e(TAG, "adb pair failed");
                    context = "adb pair failed";
                    notifyNotification(title, context);
                    return;
                }

                stopSearch();

                title = "success";
                context = "start shell cmd";
                notifyNotification(title, context);
            }
        }.start();

        startForeground(notificationId, mWorkingNotification);
    }

    private void handleStop(){
        Log.d(TAG, "handleStop");
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification getNotificationByPort(){
        int port;
        synchronized (mPort){
            port = mPort;
        }

        Notification notification = mSearchNotification;
        if (port != PairPortListener.UNAVAILABLE_PORT){
            notification = mInputNotification;
        }

        return notification;
    }

    class PairPortListener implements AdbMDNS.PortListener{
        public static final int UNAVAILABLE_PORT = -1;
        private Handler mHandler;

        PairPortListener(Handler handler){
            mHandler = handler;
        }

        @Override
        public void setPort(int port) {
            synchronized (mPort) {
                mPort = port;
            }

            mHandler.post(() -> {
                Notification notification = getNotificationByPort();
                startForeground(notificationId, notification);
            });
        }
    }
}