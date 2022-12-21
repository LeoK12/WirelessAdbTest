package com.leok12.grabservice;

import java.util.Locale;

public class GrabKeyData extends GrabData {
    private int keyCode;
    private int action;

    public GrabKeyData(int deviceId, int keyCode, int action) {
        super(deviceId, GRAB_KEY_DATA);
        this.keyCode = keyCode;
        this.action = action;
    }

    public GrabKeyData(GrabKeyData data){
        super(data.getDeviceId(), GRAB_KEY_DATA);
        this.keyCode = data.getKeyCode();
        this.action = data.getAction();
    }

    @Override
    public int getDeviceId() {
        return super.getDeviceId();
    }

    @Override
    public int getType() {
        return super.getType();
    }

    public int getKeyCode() {
        return keyCode;
    }

    public int getAction() {
        return action;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "Key {deviceId = %d, action = %d, keyCode = %d}", getDeviceId(), action, keyCode);
    }
}
