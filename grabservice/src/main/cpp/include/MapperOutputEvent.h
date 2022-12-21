//
// Created by makx on 2023/2/2.
//

#ifndef WIRELESSADBTEST_MAPPEROUTPUTEVENT_H
#define WIRELESSADBTEST_MAPPEROUTPUTEVENT_H

#define MAX_POINTER_COUNT 10

#define OUTPUT_INVALID_EVENT -1
#define OUTPUT_MOTION_EVENT 0
#define OUTPUT_KEY_EVENT    1

struct OutputEvent {
    int deviceId;
    int type;
};

struct OutputPointer {
    uint32_t id;
    int32_t x;
    int32_t y;
    int32_t touchMajor;
    int32_t touchMinor;
    int32_t widthMajor;
    int32_t widthMinor;
    int32_t orientation;
    int32_t pressure;
    int32_t distance;
    int32_t tooltype;
};

struct OutputMotionEvent{
    OutputEvent head;
    int action;
    int pointerCount;
    OutputPointer pointers[MAX_POINTER_COUNT];
};

struct OutputKeyEvent{
    OutputEvent head;
    int32_t keyCode;
    int32_t action;
};

#endif //WIRELESSADBTEST_MAPPEROUTPUTEVENT_H
