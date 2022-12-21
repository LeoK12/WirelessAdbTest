package com.leok12.wirelessadbtest;

import android.net.ssl.SSLEngines;
import android.net.ssl.SSLSockets;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.org.conscrypt.Conscrypt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;

@RequiresApi(Build.VERSION_CODES.R)
public class AdbPair {
    public static final String TAG = AdbPair.class.getSimpleName();
    private static final int PAIRING_PACKET_HEADER_SIZE = 6;

    private static final String EXPORT_KEY_LABEL = "adb-label\u0000";
    private static final int EXPORT_KEY_SIZE = 64;

    private String mHost;
    private int mPort;
    private String mCode;
    private AdbKey mKey;
    private DataInputStream mInputStream;
    private DataOutputStream mOutputStream;
    private PairingAuthCtx mPairingAuthCtx;
    private PeerInfo mPeerInfo;

    public AdbPair(String host, int port, String code, AdbKey key){
        this.mHost = host;
        this.mPort = port;
        this.mCode = code;
        this.mKey = key;
        this.mPeerInfo = new PeerInfo(PeerInfo.Type.ADB_RSA_PUB_KEY.getValue(), mKey.getAdbPublicKey());
    }

    public boolean start(){
        if (!setupTlsConnection()){
            Log.e(TAG, "faileld to setup Tls Connection");
            return false;
        }

        if (!doExchangeMsgs()){
            Log.e(TAG, "faileld to exchange msgs");
            return false;
        }

        if (!doExchangePeerInfo()){
            Log.e(TAG, "faileld to exchange peer info");
            return false;
        }

        return true;
    }

    private PairingPacketHeader readHeader(){
        byte[] bytes = new byte[PAIRING_PACKET_HEADER_SIZE];
        if (mInputStream == null){
            Log.e(TAG, "mInputStream should not be null");
            return null;
        }

        try {
            mInputStream.readFully(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        return PairingPacketHeader.Companion.readFrom(buffer);
    }

    private void writeHeader(PairingPacketHeader header, byte[] payload){
        ByteBuffer buffer = ByteBuffer.allocate(PAIRING_PACKET_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        header.writeTo(buffer);

        if (mOutputStream == null){
            return;
        }

        try {
            mOutputStream.write(buffer.array());
            mOutputStream.write(payload);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private PairingPacketHeader createHeader(PairingPacketHeader.Type type, int payload){
        return new PairingPacketHeader(PairingPacketHeader.CURRENT_KEY_HEADER_VERSION, type.getValue(), payload);
    }

    private boolean doExchangePeerInfo(){
        ByteBuffer buffer = ByteBuffer.allocate(PeerInfo.MAX_PERR_INFO_SIZE).order(ByteOrder.BIG_ENDIAN);
        if (mPeerInfo == null){
            Log.e(TAG, "mPeerInfo should not be null");
            return false;
        }

        mPeerInfo.writeTo(buffer);

        if (mPairingAuthCtx == null){
            Log.e(TAG, "mPairingAuthCtx should not be null");
            return false;
        }

        byte[] encrypt = mPairingAuthCtx.encrypt(buffer.array());
        if (encrypt == null){
            Log.e(TAG, "encrypt should not be null");
            return false;
        }

        PairingPacketHeader ourHeader = createHeader(PairingPacketHeader.Type.PEER_INFO, encrypt.length);
        writeHeader(ourHeader, encrypt);
        PairingPacketHeader theirHeader = readHeader();
        if (theirHeader == null){
            Log.e(TAG, "theirHeader should not be null");
            return false;
        }

        if (theirHeader.getType() != PairingPacketHeader.Type.PEER_INFO.getValue()){
            Log.e(TAG, "theirHeader.getType = " + theirHeader.getType());
            Log.e(TAG, "PairingPacketHeader.Type.PEER_INFO.getValue() = " + PairingPacketHeader.Type.PEER_INFO.getValue());
            return false;
        }

        byte[] theirMessage = new byte[theirHeader.getPayload()];
        if (mInputStream == null){
            Log.e(TAG, "mInputStream should not be null");
            return false;
        }

        try {
            mInputStream.readFully(theirMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] decrypt = mPairingAuthCtx.decrypt(theirMessage);
        if (decrypt == null){
            Log.e(TAG, "decrypt should not be null");
            return false;
        }

        if (decrypt.length != PeerInfo.MAX_PERR_INFO_SIZE){
            Log.e(TAG, "decrypt.length = " + decrypt.length);
            return false;
        }

        return true;
    }

    private boolean doExchangeMsgs(){
        if (mPairingAuthCtx == null){
            return false;
        }

        byte[] msg = mPairingAuthCtx.getMsg();
        PairingPacketHeader header = createHeader(PairingPacketHeader.Type.SPAKE2_MSG, msg.length);
        writeHeader(header, msg);
        PairingPacketHeader theirHeader = readHeader();
        if (theirHeader == null){
            return false;
        }

        if (theirHeader.getType() != PairingPacketHeader.Type.SPAKE2_MSG.getValue()){
            return false;
        }

        byte[] theirMsg = new byte[theirHeader.getPayload()];
        try {
            mInputStream.readFully(theirMsg);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return mPairingAuthCtx.initCipher(theirMsg);
    }

    private boolean setupTlsConnection(){
        try {
            Socket socket = new Socket(mHost, mPort);

            socket.setTcpNoDelay(true);
            SSLContext sslContext = mKey.getSSLContext();
            SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory()
                    .createSocket(socket, mHost, mPort, true);
            if (sslSocket == null){
                Log.e(TAG, "failed to create ssl connection");
                return false;
            }

            sslSocket.startHandshake();

            mInputStream = new DataInputStream(sslSocket.getInputStream());
            mOutputStream = new DataOutputStream(sslSocket.getOutputStream());

            byte[] codeBytes = mCode.getBytes(StandardCharsets.UTF_8);
            if (codeBytes == null){
                Log.e(TAG, "codeBytes should not be null");
                return false;
            }

//            byte[] exportKeyingMaterial = SSLSockets.exportKeyingMaterial(
//                    sslSocket, EXPORT_KEY_LABEL, null, EXPORT_KEY_SIZE);
            byte[] exportKeyingMaterial = Conscrypt.exportKeyingMaterial(
                    sslSocket, EXPORT_KEY_LABEL, null , EXPORT_KEY_SIZE);
            if (exportKeyingMaterial == null){
                Log.e(TAG, "exportKeyingMaterial should not be null");
                return false;
            }

            byte[] pwdBytes = new byte[codeBytes.length + exportKeyingMaterial.length];
            System.arraycopy(codeBytes, 0, pwdBytes, 0, codeBytes.length);
            System.arraycopy(exportKeyingMaterial, 0, pwdBytes, codeBytes.length, exportKeyingMaterial.length);

            PairingAuthCtx pairingAuthCtx = PairingAuthCtx.create(true, pwdBytes);
            if (pairingAuthCtx == null){
                Log.e(TAG, "failed to create pairing auth ctx");
                return false;
            }

            mPairingAuthCtx = pairingAuthCtx;
        } catch (Exception e){
            e.printStackTrace();
        }

        return true;
    }
}
