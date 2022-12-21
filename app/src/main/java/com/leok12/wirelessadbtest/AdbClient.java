package com.leok12.wirelessadbtest;

import static com.leok12.wirelessadbtest.AdbProtocol.ADB_AUTH_RSAPUBLICKEY;
import static com.leok12.wirelessadbtest.AdbProtocol.ADB_AUTH_SIGNATURE;
import static com.leok12.wirelessadbtest.AdbProtocol.A_AUTH;
import static com.leok12.wirelessadbtest.AdbProtocol.A_CLSE;
import static com.leok12.wirelessadbtest.AdbProtocol.A_CNXN;
import static com.leok12.wirelessadbtest.AdbProtocol.A_MAXDATA;
import static com.leok12.wirelessadbtest.AdbProtocol.A_OKAY;
import static com.leok12.wirelessadbtest.AdbProtocol.A_OPEN;
import static com.leok12.wirelessadbtest.AdbProtocol.A_STLS;
import static com.leok12.wirelessadbtest.AdbProtocol.A_STLS_VERSION;
import static com.leok12.wirelessadbtest.AdbProtocol.A_VERSION;
import static com.leok12.wirelessadbtest.AdbProtocol.A_WRTE;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

public class AdbClient {
    private static final String TAG = AdbClient.class.getSimpleName();

    private final String mHost;
    private final int mPort;
    private final AdbKey mAdbKey;

    private Socket mSocket;
    private DataInputStream mInputStream;
    private DataOutputStream mOutputStream;

    private boolean mUseSSL;
    private SSLSocket mSSLSocket;
    private DataInputStream mSSLInputStream;
    private DataOutputStream mSSLOutputStream;

    AdbClient(String host, int port, AdbKey key){
        mHost = host;
        mPort = port;
        mAdbKey = key;
        mUseSSL = false;
    }

    private void write(AdbMessage message){
        try {
            getOutputStream().write(message.toByteArray());
            getOutputStream().flush();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public DataInputStream getInputStream() {
        return mUseSSL ? mSSLInputStream : mInputStream;
    }

    public DataOutputStream getOutputStream() {
        return mUseSSL ? mSSLOutputStream : mOutputStream;
    }

    private AdbMessage read(){
        try {
            ByteBuffer buffer = ByteBuffer.allocate(AdbMessage.HEADER_LENGTH);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            getInputStream().readFully(buffer.array(), 0, AdbMessage.HEADER_LENGTH);
            int command = buffer.getInt();
            int arg0 = buffer.getInt();
            int arg1 = buffer.getInt();
            int data_length = buffer.getInt();
            int data_crc32 = buffer.getInt();
            int magic = buffer.getInt();
            byte[] data = null;
            if (data_length > 0){
                data = new byte[data_length];
                getInputStream().readFully(data, 0, data_length);
            }

            return new AdbMessage(command, arg0, arg1, data_length, data_crc32, magic, data);
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public boolean connect(){
        try {
            mSocket = new Socket(mHost, mPort);
            if (mSocket == null){
                Log.e(TAG, "mSocket should not be null");
                return false;
            }

            mSocket.setTcpNoDelay(true);
            mInputStream = new DataInputStream(mSocket.getInputStream());
            mOutputStream = new DataOutputStream(mSocket.getOutputStream());

            write(new AdbMessage(A_CNXN, A_VERSION, A_MAXDATA, "host::"));
            AdbMessage message = read();
            if (message.getCommand() == A_STLS){
                write(new AdbMessage(A_STLS, A_STLS_VERSION, 0, (byte[]) null));
                SSLContext sslContext = mAdbKey.getSSLContext();
                mSSLSocket = (SSLSocket) sslContext.getSocketFactory().
                        createSocket(mSocket, mHost, mPort, true);
                mSSLSocket.startHandshake();

                mSSLInputStream = new DataInputStream(mSSLSocket.getInputStream());
                mSSLOutputStream = new DataOutputStream(mSSLSocket.getOutputStream());
                mUseSSL = true;

                message = read();
            } else if (message.getCommand() == A_AUTH){
                write(new AdbMessage(A_AUTH, ADB_AUTH_SIGNATURE, 0, mAdbKey.sign(message.getData())));
                message = read();
                if (message.getCommand() != A_CNXN){
                    write(new AdbMessage(A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, mAdbKey.getAdbPublicKey()));
                    message = read();
                }
            }

            return message.getCommand() != A_CNXN;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean run(String cmd){
        int localId = 1;
        write(new AdbMessage(A_OPEN, localId, 0, "shell:" + cmd));
        AdbMessage message = read();
        int command = message.getCommand();
        switch (command){
            case A_OKAY:
                while (true){
                    message = read();
                    int remoteId = message.getArg0();
                    command = message.getCommand();
                    if (command == A_WRTE){
                        if (message.getData_length() > 0){
                            Log.d(TAG, "message = " + new String(message.getData(), StandardCharsets.UTF_8));
                        }
                        write(new AdbMessage(A_OKAY, localId, remoteId, (byte[]) null));
                    } else if (command == A_CLSE){
                        write(new AdbMessage(A_CLSE, localId, remoteId, (byte[]) null));
                        break;
                    }
                }
                break;
            case A_CLSE:
                int remoteId = message.getArg0();
                write(new AdbMessage(A_CLSE, localId, remoteId, (byte[]) null));
                break;
            default:
                Log.e(TAG, "not A_OKEY or A_CLSE");
                return false;
        }

        return true;
    }

    public void close(){
        try {
            mInputStream.close();
            mOutputStream.close();
            mSocket.close();
            if (mUseSSL){
                mSSLInputStream.close();
                mSSLOutputStream.close();
                mSSLSocket.close();
            }
        } catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
    }
}
