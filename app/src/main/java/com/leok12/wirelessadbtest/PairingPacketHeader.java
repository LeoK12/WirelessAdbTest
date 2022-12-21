package com.leok12.wirelessadbtest;

import android.util.Log;

import java.nio.ByteBuffer;

class PairingPacketHeader{
    public static final byte CURRENT_KEY_HEADER_VERSION = 1;
    public static final byte MIN_SUPPORT_KEY_HEADER_VERSION = 1;
    public static final byte MAX_SUPPORT_KEY_HEADER_VERSION = 1;
    public static final int MAX_PAY_LOAD_SIZE = PeerInfo.MAX_PERR_INFO_SIZE * 2;

    private final byte version;
    private final byte type;
    private final int payload;
    public static final Companion COMPANION = new PairingPacketHeader.Companion();

    PairingPacketHeader(byte version, byte type, int payload){
        this.version = version;
        this.type = type;
        this.payload = payload;
    }

    public void writeTo(ByteBuffer buffer){
        buffer.put(this.version);
        buffer.put(this.type);
        buffer.putInt(this.payload);
    }

    public byte getType() {
        return type;
    }

    public byte getVersion() {
        return version;
    }

    public int getPayload() {
        return payload;
    }

    public static final class Companion {
        public static PairingPacketHeader readFrom(ByteBuffer buffer) {
            byte version = buffer.get();
            byte type = buffer.get();
            int payload = buffer.getInt();
            if (version < MIN_SUPPORT_KEY_HEADER_VERSION || version > MAX_SUPPORT_KEY_HEADER_VERSION) {
                Log.e(AdbPair.TAG, "version = " + version);
                return null;
            }

            if (type != Type.SPAKE2_MSG.getValue() && type != Type.PEER_INFO.getValue()) {
                Log.e(AdbPair.TAG, "type = " + type);
                Log.e(AdbPair.TAG, "SPAKE2_MSG = " + Type.SPAKE2_MSG.getValue());
                Log.e(AdbPair.TAG, "PEER_INFO = " + Type.PEER_INFO.getValue());
                return null;
            }

            if (payload <= 0 || payload > MAX_PAY_LOAD_SIZE) {
                Log.e(AdbPair.TAG, "payload = " + payload);
                return null;
            }

            return new PairingPacketHeader(version, type, payload);
        }
    }

    public enum Type{
        SPAKE2_MSG(Byte.valueOf("0")),
        PEER_INFO(Byte.valueOf("1"));

        private byte value;

        Type(Byte value) {
            this.value = value;
        }

        public byte getValue() {
            return value;
        }
    }
}
