package com.leok12.wirelessadbtest;

public class PairingAuthCtx {
    static {
        System.loadLibrary("adb");
    }

    private long mPtr;
    private byte[] mMsg;

    private static native long nativeConstructor(boolean isClient, byte[] pwd);
    private native byte[] nativeMsg(long ptr);
    private native boolean nativeInitCipher(long ptr, byte[] data);
    private native byte[] nativeEncrypt(long ptr, byte[] data);
    private native byte[] nativeDecrypt(long ptr, byte[] data);
    private native void nativeDestroy(long ptr);

    public static PairingAuthCtx create(boolean isClient, byte[] pwd){
        long ptr = nativeConstructor(isClient, pwd);
        if (ptr == 0){
            return null;
        }

        return new PairingAuthCtx(ptr);
    }

    PairingAuthCtx(long ptr){
        mPtr = ptr;
        mMsg = nativeMsg(mPtr);
    }

    public boolean initCipher(byte[] data){
        return nativeInitCipher(mPtr, data);
    }

    public byte[] encrypt(byte[] data){
        return nativeEncrypt(mPtr, data);
    }

    public byte[] decrypt(byte[] data){
        return nativeDecrypt(mPtr, data);
    }

    public void destroy(){
        nativeDestroy(mPtr);
    }

    public byte[] getMsg() {
        return mMsg;
    }
}
