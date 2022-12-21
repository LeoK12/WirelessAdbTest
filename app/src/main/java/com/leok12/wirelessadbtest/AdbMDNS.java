package com.leok12.wirelessadbtest;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;

@RequiresApi(Build.VERSION_CODES.R)
public class AdbMDNS {
    private static final String TAG = AdbMDNS.class.getSimpleName();
    public static final String ADB_MDNS_TLS_PAIRING_TYPE = "_adb-tls-pairing._tcp";
    public static final String ADB_MDNS_TLS_CONNECT_TYPE = "_adb-tls-connect._tcp";
    public static final String LOCALHOST = "127.0.0.1";
    public static final int UNAVAILABLE_PORT = -1;

    private Context mContext;
    private String mType;
    private NsdManager mNsdManager;
    private String mServiceName;
    private DiscoveryListener mDiscoveryListener;
    private PortListener mPortListener;
    private boolean mIsRunning;

    public AdbMDNS(Context context, String type){
        mContext = context;
        mType = type;
        mNsdManager = mContext.getSystemService(NsdManager.class);
        mDiscoveryListener = new DiscoveryListener();
        mIsRunning = false;
    }

    public void start(){
        if (mIsRunning){
            return;
        }

        mIsRunning = true;
        mNsdManager.discoverServices(mType, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    public void stop(){
        if (!mIsRunning){
            return;
        }

        mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        mIsRunning = false;
    }

    public void setPortListener(PortListener listener){
        mPortListener = listener;
    }

    class DiscoveryListener implements NsdManager.DiscoveryListener{

        @Override
        public void onStartDiscoveryFailed(String s, int i) {
            Log.d(TAG, String.format("onStartDiscoveryFailed: s = %s, i = %d", s, i));
        }

        @Override
        public void onStopDiscoveryFailed(String s, int i) {
            Log.d(TAG, String.format("onStopDiscoveryFailed: s = %s, i = %d", s, i));
        }

        @Override
        public void onDiscoveryStarted(String s) {
            Log.d(TAG, String.format("onDiscoveryStarted: s = %s", s));
        }

        @Override
        public void onDiscoveryStopped(String s) {
            Log.d(TAG, String.format("onDiscoveryStopped: s = %s", s));
        }

        @Override
        public void onServiceFound(NsdServiceInfo info) {
            Log.d(TAG, "onServiceFound : info = " + info);
            mNsdManager.resolveService(info, new ResoverListener());
        }

        @Override
        public void onServiceLost(NsdServiceInfo info) {
            if (info.getServiceName().equals(mServiceName)){
                if (mPortListener != null){
                    mPortListener.setPort(UNAVAILABLE_PORT);
                }
            }
        }
    }

    class ResoverListener implements NsdManager.ResolveListener{
        @Override
        public void onResolveFailed(NsdServiceInfo info, int i) {

        }

        private boolean findHostAddress(String hostAddress){
            Log.e(TAG, "findHostAddress : hostAddress = " + hostAddress);
            Enumeration<NetworkInterface> interfaces = null;
            try {
                interfaces = NetworkInterface.getNetworkInterfaces();
            } catch (SocketException e) {
                e.printStackTrace();
            }

            for (NetworkInterface inteface : Collections.list(interfaces)) {
                Enumeration<InetAddress> addresses = inteface.getInetAddresses();
                for (InetAddress address : Collections.list(addresses)) {
                    Log.e(TAG, "findHostAddress : address.getHostAddress() = " + address.getHostAddress());
                    if (!hostAddress.equals(address.getHostAddress())){
                        continue;
                    }

                    return true;
                }
            }

            return false;
        }

        private boolean isPortAvailable(int port){
            boolean availble = false;
            try {
                ServerSocket serverSocket = new ServerSocket();

                try {
                    serverSocket.bind(new InetSocketAddress(LOCALHOST, port), 1);
                } catch (IOException e){
                    throw e;
                } finally {
                    serverSocket.close();
                }
            } catch (IOException e){
                availble = true;
            }

            return availble;
        }

        @Override
        public void onServiceResolved(NsdServiceInfo info) {
            Log.e(TAG, "info = " + info.toString());
            if (!findHostAddress(info.getHost().getHostAddress())){
                Log.e(TAG, "failed to find host address for " + info.getHost().getHostAddress());
                return;
            }

            if (!isPortAvailable(info.getPort())){
                Log.e(TAG, info.getPort() + "is not available");
                return;
            }

            mServiceName = info.getServiceName();
            if (mPortListener != null){
                mPortListener.setPort(info.getPort());
            }
        }
    }

    public interface PortListener{
        void setPort(int port);
    }
}
