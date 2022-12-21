package com.leok12.grabservice;

public class GrabData {
    private int deviceId;

    public static final int GRAB_MOTION_DATA = 0;
    public static final int GRAB_KEY_DATA    =  1;
    private int type;

    public GrabData(int deviceId, int type){
        this.deviceId = deviceId;
        this.type = type;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public int getType() {
        return type;
    }
}
