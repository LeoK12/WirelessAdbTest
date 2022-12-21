//
// Created by makx on 2022/12/21.
//

#ifndef WIRELESSADBTEST_INPUTREADER_H
#define WIRELESSADBTEST_INPUTREADER_H

#include "EventHub.h"
#include "InputDevice.h"
#include "MapperEventQueue.h"
#include <unordered_map>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "inputreader"

#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class InputReader{
public:
    InputReader(std::shared_ptr<EventHub> eventHub, MapperEventQueue* queue);
    ~InputReader();
    int start();
    int stop();
    void updateDisplayInfo(int width, int height, int rotation);
    int openDevice(char* path);
    int closeDevice(char* path);
    void processEvent(int fd, struct input_event* events, size_t count);
    void updateGrabState(bool enable);
    char* getAppSwitchPath(){return mEventHub->getAppSwitchPath();}

private:
    bool mIsGrabEnabled;
    int32_t mNextDeviceId;
    std::shared_ptr<EventHub> mEventHub;
    MapperEventQueue* mQueue;
    std::unordered_map<int32_t, std::unique_ptr<InputDevice>> mDevices;
    int mDisplayWidth;
    int mDisplayHeight;
    int mDisplayRotation;
};

#endif //WIRELESSADBTEST_INPUTREADER_H
