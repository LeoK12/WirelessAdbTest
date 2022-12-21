package com.leok12.wirelessadbtest;

import java.nio.ByteBuffer;

class PeerInfo{
    public static final int MAX_PERR_INFO_SIZE = 8192;
    private final byte type;
    private final byte[] data;
    public static final Companion COMPANION = new PeerInfo.Companion();

    PeerInfo(byte type, byte[] data){
        this.type = type;
        this.data = new byte[MAX_PERR_INFO_SIZE - 1];
        System.arraycopy(data, 0, this.data, 0, Math.min(data.length, (MAX_PERR_INFO_SIZE - 1)));
    }

    public void writeTo(ByteBuffer buffer){
        buffer.put(this.type);
        buffer.put(this.data);
    }

    public enum Type {
        ADB_RSA_PUB_KEY(Byte.valueOf("0")),
        ADB_DEVICE_GUID(Byte.valueOf("0"));

        private final byte value;

        public final byte getValue(){
            return this.value;
        }

        Type(byte value){
            this.value = value;
        }
    }

    public static final class Companion {
        public final PeerInfo readFrom( ByteBuffer buffer) {
            byte type = buffer.get();
            byte[] data = new byte[MAX_PERR_INFO_SIZE - 1];
            buffer.get(data);
            return new PeerInfo(type, data);
        }
    }
}
