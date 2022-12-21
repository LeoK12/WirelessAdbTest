package com.leok12.wirelessadbtest;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class AdbMessage {
    public static final int HEADER_LENGTH = 24;

    private final int command;
    private final int arg0;
    private final int arg1;
    private final int data_length;
    private final int data_crc32;
    private final int magic;
    private final byte[] data;

    public AdbMessage(int command, int arg0, int arg1, String data){
        this(command,
                arg0,
                arg1,
                (data + '\u0000').getBytes(StandardCharsets.UTF_8));
    }

    public AdbMessage(int command, int arg0, int arg1, byte[] data){
        this(command,
                arg0,
                arg1,
                data != null ? data.length : 0,
                crc32(data),
                (int)((long)command ^ 0xFFFFFFFF),
                data);
    }

    public AdbMessage(int command, int arg0, int arg1, int data_length, int data_crc32, int magic, byte[] data) {
        this.command = command;
        this.arg0 = arg0;
        this.arg1 = arg1;
        this.data_length = data_length;
        this.data_crc32 = data_crc32;
        this.magic = magic;
        this.data = data;
    }

    private static final int crc32(byte[] data) {
        if (data == null) {
            return 0;
        } else {
            int res = 0;
            byte[] var5 = data;
            int var6 = data.length;

            for(int var4 = 0; var4 < var6; ++var4) {
                byte b = var5[var4];
                if (b >= 0) {
                    res += b;
                } else {
                    res += b + 256;
                }
            }

            return res;
        }
    }

    public byte[] toByteArray(){
        int length = HEADER_LENGTH;
        if (data != null){
            length += data.length;
        }

        ByteBuffer buffer = ByteBuffer.allocate(length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(this.command);
        buffer.putInt(this.arg0);
        buffer.putInt(this.arg1);
        buffer.putInt(this.data_length);
        buffer.putInt(this.data_crc32);
        buffer.putInt(this.magic);
        if (this.data != null){
            buffer.put(this.data);
        }

        return buffer.array();
    }

    public int getCommand() {
        return command;
    }

    public byte[] getData() {
        return data;
    }

    public int getArg0() {
        return arg0;
    }

    public int getArg1() {
        return arg1;
    }

    public int getData_length() {
        return data_length;
    }

    public int getData_crc32() {
        return data_crc32;
    }

    public int getMagic() {
        return magic;
    }

    @Override
    public String toString() {
        return "AdbMessage{" +
                "command=" + command +
                ", arg0=" + arg0 +
                ", arg1=" + arg1 +
                ", data_length=" + data_length +
                ", data_crc32=" + data_crc32 +
                ", magic=" + magic +
                ", data=" + Arrays.toString(data) +
                '}';
    }
}
