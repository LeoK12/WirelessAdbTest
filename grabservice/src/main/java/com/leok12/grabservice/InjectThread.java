package com.leok12.grabservice;

import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

import com.leok12.grabservice.reflectClass.InputManager;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

public class InjectThread extends Thread {
    private static final String TAG = InjectThread.class.getSimpleName();
    public static final int INVALID_POINTER_ID                 = -1;
    private static final int MAX_POINTER_COUNT                 = 16;
    private static final int INTER_TOUCH_DEVICE_FIRST_TOUCH_ID = 0;
    private static final int EXTRA_TOUCH_DEVICE_TOUCH_ID_SIZE  = 10;
    private int mNextExtraTouchDeviceFirstTouchId = INTER_TOUCH_DEVICE_FIRST_TOUCH_ID + EXTRA_TOUCH_DEVICE_TOUCH_ID_SIZE;
    private Queue<GrabData> mDataQueue = new LinkedBlockingDeque<>();
    private final ArrayMap<Integer, Integer> mExtraTouchDeviceMapper = new ArrayMap<>();
    private Pos[] mTouchList;

    @Override
    public void run() {
        while (true){
            GrabData data;
            synchronized (mDataQueue) {
                data = mDataQueue.poll();
                if (data == null){
                    try {
                        mDataQueue.wait();
                    } catch (Exception e){
                        e.printStackTrace();
                    }

                    data = mDataQueue.poll();
                }
            }

            if (data == null){
                continue;
            }

            boolean ret = process(data);
            Log.e(TAG, "ret = " + ret);
        }
    }

    private boolean process(GrabData data){
        if (data.getType() == GrabData.GRAB_KEY_DATA){
            GrabKeyData key = (GrabKeyData)data;
            KeyEvent keyEvent = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                    key.getAction(), key.getKeyCode(), 0, 0, key.getDeviceId(),
                    0, 0, InputDevice.SOURCE_KEYBOARD);
            boolean ret = fireKeyEvent(keyEvent);
        } else {
            GrabMotionData motion = (GrabMotionData)data;
            Integer firstTouchId = INTER_TOUCH_DEVICE_FIRST_TOUCH_ID;
            int deviceId = data.getDeviceId();
            if (deviceId > 0) {
                firstTouchId = mExtraTouchDeviceMapper.get(deviceId);
                if (firstTouchId == null) {
                    firstTouchId = mNextExtraTouchDeviceFirstTouchId;
                    mNextExtraTouchDeviceFirstTouchId += EXTRA_TOUCH_DEVICE_TOUCH_ID_SIZE;
                    mExtraTouchDeviceMapper.put(deviceId, firstTouchId);
                }
            }

            int count = motion.getPointerCount();
            int actionIndex = INVALID_POINTER_ID;
            for (int i = 0; i < count; i++) {
                Pos pos = new Pos(motion.getX(i), motion.getY(i));
                PointerCoords pointerCoords = new PointerCoords();
                pointerCoords.x = motion.getX(i);
                pointerCoords.y = motion.getY(i);
                pointerCoords.touchMajor = motion.getTouchMajor(i);
                pointerCoords.touchMinor = motion.getTouchMinor(i);
                pointerCoords.toolMajor = motion.getToolMajor(i);
                pointerCoords.toolMinor = motion.getToolMinor(i);
                pointerCoords.orientation = motion.getOrientation(i);
                pointerCoords.pressure = motion.getPressure(i);
                pointerCoords.setAxisValue(MotionEvent.AXIS_DISTANCE, motion.getDistance(i));
                pos.setPointerCoords(pointerCoords);
                pos.setTouchId(firstTouchId + motion.getPointerId(i));
                if (i == motion.getActionIndex()) {
                    actionIndex = insertTouch(pos);
                } else {
                    insertTouch(pos);
                }
            }

            if (actionIndex == INVALID_POINTER_ID) {
                Log.e(TAG, "invalid action index");
            }

            MotionEvent motionEvent = buildMotionEvent(actionIndex,
                    motion.getAction() & MotionEvent.ACTION_MASK);
            boolean ret = fireMotioEvent(motionEvent);
            if (!ret) {
                removeTouch(actionIndex);
            }
        }
        return true;
    }

    public InjectThread(){
        mTouchList = new Pos[MAX_POINTER_COUNT];
        for (int i=0;i<MAX_POINTER_COUNT;i++){
            mTouchList[i] = new Pos();
        }
    }

    public void queue(GrabMotionData data){
        synchronized (mDataQueue){
            try {
                mDataQueue.add(new GrabMotionData(data));
            } catch (Exception e){
                e.printStackTrace();
            }

            mDataQueue.notify();
        }
    }

    public void queue(GrabKeyData data){
        synchronized (mDataQueue){
            try {
                mDataQueue.add(new GrabKeyData(data));
            } catch (Exception e){
                e.printStackTrace();
            }

            mDataQueue.notify();
        }
    }

    private int insertTouch(Pos pos){
        int index = -1;
        for (int i=0;i<MAX_POINTER_COUNT;i++){
            if (mTouchList[i].isValid()){
                index++;
                if (mTouchList[i].getTouchId() == pos.getTouchId()){
                    mTouchList[i].setPos(pos);
                    return index;
                }
            }
        }

        for (int i=0;i<MAX_POINTER_COUNT;i++){
            if (!mTouchList[i].isValid()){
                mTouchList[i].setPos(pos);
                return i;
            }
        }

        return -1;
    }

    private boolean removeTouch(int actionIndex){
        if (actionIndex < 0 || actionIndex >= MAX_POINTER_COUNT){
            return false;
        }

        for (int i=0;i<MAX_POINTER_COUNT;i++){
            if (mTouchList[i].isValid()){
                if (actionIndex == 0){
                    mTouchList[i].reset();
                    break;
                }

                actionIndex--;
            }
        }

        return true;
    }

    private MotionEvent buildMotionEvent(int actionIndex, int action) {
        int nCount = 0;
        ArrayList<PointerCoords> pointerCoordsList = new ArrayList<PointerCoords>();
        ArrayList<PointerProperties> pointerPropsList = new ArrayList<PointerProperties>();

        for (int i=0;i<MAX_POINTER_COUNT;i++) {
            if (!mTouchList[i].isValid()) {
                continue;
            }

            PointerProperties pointerProps = new PointerProperties();
            pointerProps.id = i;
            pointerProps.toolType = MotionEvent.TOOL_TYPE_FINGER;
            pointerPropsList.add(pointerProps);

            PointerCoords pointerCoords = new PointerCoords();
            pointerCoords.copyFrom(mTouchList[i].getPointerCoords());
            pointerCoordsList.add(pointerCoords);
            nCount++;
        }

        if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_POINTER_UP ||
                action == MotionEvent.ACTION_HOVER_EXIT ||
                action == MotionEvent.ACTION_HOVER_MOVE ||
                action == MotionEvent.ACTION_CANCEL){
            removeTouch(actionIndex);
        }

        if (nCount == 0) {
            return null;
        }

        if (nCount > 1) {
            if (action != MotionEvent.ACTION_MOVE) {
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        action = MotionEvent.ACTION_POINTER_DOWN;
                        break;
                    case MotionEvent.ACTION_UP:
                        action = MotionEvent.ACTION_POINTER_UP;
                        break;
                    default:
                        break;
                }

                action |= actionIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
            }
        }

        MotionEvent motionEvent = MotionEvent.obtain(  SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                action,
                nCount,
                pointerPropsList.toArray(new PointerProperties[pointerPropsList.size()]),
                pointerCoordsList.toArray(new PointerCoords[pointerCoordsList.size()]),
                0, // metaState
                0, // buttonState
                1, // xPrecision
                1, // yPrecision
                0, // deviceId
                0, // edgeFlags
                InputDevice.SOURCE_TOUCHSCREEN, // source
                0  // flags
        );
        return motionEvent;
    }

    private boolean fireKeyEvent(KeyEvent event){
        boolean ret = false;
        if (event == null) {
            return ret;
        }

        Log.e(TAG, "event = " + event.toString());
        int mode = InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH;

        ret = InputManager.injectInputEvent(event, mode);
        if (!ret){
            Log.e(TAG, "fire FAILED ! event = " + event.toString());
        }

        return ret;
    }

    private boolean fireMotioEvent(MotionEvent event) {
        boolean ret = false;
        if (event == null) {
            return ret;
        }

        int mode = InputManager.INJECT_INPUT_EVENT_MODE_ASYNC;
        Log.e(TAG, "event = " + ((MotionEvent)event).toString());
        int action = ((MotionEvent)event).getAction() & MotionEvent.ACTION_MASK;
        if (action == MotionEvent.ACTION_DOWN ||
                action == MotionEvent.ACTION_POINTER_DOWN) {
            mode = InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH;
        }

        ret = InputManager.injectInputEvent(event, mode);
        if (!ret){
            Log.e(TAG, "fire FAILED ! event = " + event.toString());
        }

        return ret;
    }

    private class Pos {
        private int touchId;
        private boolean fromTouch;
        private MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();

        public Pos(){
            reset();
        }

        public Pos(float x, float y){
            this.fromTouch = false;
            this.touchId = -1;
            this.pointerCoords.x = x;
            this.pointerCoords.y = y;
        }

        public void reset(){
            this.fromTouch = false;
            this.touchId = -1;
            this.pointerCoords.clear();
        }

        public boolean isValid(){
            return touchId != -1;
        }

        public void setX(float x){
            this.pointerCoords.x = x;
        }

        public void setY(float y){
            this.pointerCoords.y = y;
        }

        public void setPressure(float pressure){
            this.pointerCoords.pressure = pressure;
        }

        public void setTouchId(int touchId){
            this.touchId = touchId;
        }

        public void setFromTouch(boolean touch){
            this.fromTouch = touch;
        }

        public void setPointerCoords(MotionEvent.PointerCoords pointerCoords) {
            this.pointerCoords = pointerCoords;
        }

        public MotionEvent.PointerCoords getPointerCoords() {
            return pointerCoords;
        }

        public boolean getFromTouch(){
            return fromTouch;
        }

        public void setPos(Pos pos){
            this.fromTouch = pos.getFromTouch();
            this.touchId = pos.getTouchId();
            this.pointerCoords = pos.getPointerCoords();
        }

        public float getPressure(){
            return pointerCoords.pressure;
        }

        public float getX(){
            return pointerCoords.x;
        }

        public float getY(){
            return pointerCoords.y;
        }

        public int getTouchId(){
            return touchId;
        }

        public String toString(){
            return String.format(Locale.US,"Pos {pointerCoords = %s, touchId = %d}", pointerCoords, touchId);
        }
    }
}
