package com.leok12.grabservice;

import android.view.MotionEvent;

import java.util.Locale;

public class GrabMotionData extends GrabData {
    private int action;
    private Pointer[] pointers;

    private GrabMotionData(int deviceId, int action, Pointer[] pointers){
        super(deviceId, GRAB_MOTION_DATA);
        this.action = action;
        this.pointers = pointers;
    }

    public GrabMotionData(GrabMotionData data){
        super(data.getDeviceId(), GRAB_MOTION_DATA);
        this.action = data.getAction();
        int count = data.getPointerCount();
        pointers = new Pointer[count];
        for (int i = 0; i < pointers.length; i++) {
            pointers[i] = new Pointer(data.getPointerId(i), data.getX(i), data.getY(i),
                    data.getTouchMajor(i), data.getToolMinor(i), data.getToolMajor(i),
                    data.getToolMinor(i), data.getOrientation(i), data.getPressure(i),
                    data.getDistance(i), data.getTooltype(i));
        }
    }

    public int getDeviceId() {
        return super.getDeviceId();
    }

    public int getAction() {
        return action;
    }

    public int getActionIndex() {
        return (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
    }

    public int getPointerCount() {
        if (pointers == null){
            return 0;
        }

        return pointers.length;
    }

    public int getPointerId(int i) {
        if (i < 0 || i > pointers.length){
            return -1;
        }

        return pointers[i].pointerId;
    }

    public int getX(int i) {
        if (i < 0 || i > pointers.length){
            return -1;
        }
        return pointers[i].x;
    }

    public int getY(int i) {
        if (i < 0 || i > pointers.length){
            return -1;
        }

        return pointers[i].y;
    }

    public int getTouchMajor(int i) {
        if (i < 0 || i > pointers.length){
            return -1;
        }

        return pointers[i].touchMajor;
    }

    public int getTouchMinor(int i) {
        if (i < 0 || i > pointers.length){
            return -1;
        }

        return pointers[i].touchMinor;
    }

    public int getToolMajor(int i) {
        if (i < 0 || i > pointers.length){
            return -1;
        }

        return pointers[i].widthMajor;
    }

    public int getToolMinor(int i) {
        if (i < 0 || i > pointers.length){
            return -1;
        }

        return pointers[i].widthMinor;
    }

    public int getOrientation(int i) {
        if (i < 0 || i > pointers.length){
            return -1;
        }

        return pointers[i].orientation;
    }

    public int getPressure(int i) {
        if (i < 0 || i > pointers.length){
            return -1;
        }

        return pointers[i].pressure;
    }

    public int getDistance(int i) {
        if (i < 0 || i > pointers.length){
            return -1;
        }

        return pointers[i].distance;
    }

    public int getTooltype(int i) {
        if (i < 0 || i > pointers.length){
            return -1;
        }

        return pointers[i].tooltype;
    }

    private static class Pointer {
        private int pointerId;
        private int x;
        private int y;
        private int touchMajor;
        private int touchMinor;
        private int widthMajor;
        private int widthMinor;
        private int orientation;
        private int pressure;
        private int distance;
        private int tooltype;

        private Pointer(int pointerId, int x, int y, int touchMajor, int touchMinor,
                        int widthMajor, int widthMinor, int orientation, int pressure,
                        int distance, int tooltype){
            this.pointerId = pointerId;
            this.x = x;
            this.y = y;
            this.touchMajor = touchMajor;
            this.touchMinor = touchMinor;
            this.widthMajor = widthMajor;
            this.widthMinor = widthMinor;
            this.orientation = orientation;
            this.pressure = pressure;
            this.distance = distance;
            this.tooltype = tooltype;
        }

        @Override
        public String toString() {
            return String.format(Locale.US,"Pointer {pointerid = %d, x = %d, y = %d, " +
                    "touchMajor = %d, touchMinor = %d, widthMajor = %d, widthMinor = %d, " +
                    "orientation = %d, pressure = %d, distance = %d, tooltype = %d}",
                    pointerId, x, y, touchMajor, touchMinor, widthMajor, widthMinor, orientation,
                    pressure, distance, tooltype);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pointers.length; i++) {
            builder.append( "pointer[" + i +"] = " + pointers[i] + ";");
        }

        return String.format(Locale.US, "Motion {deviceId = %d, action = %d, pointers = %s}", getDeviceId(), action, builder.toString());
    }
}
