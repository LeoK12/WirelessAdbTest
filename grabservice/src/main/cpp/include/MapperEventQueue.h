//
// Created by makx on 2023/1/31.
//

#ifndef WIRELESSADBTEST_MAPPEREVENTQUEUE_H
#define WIRELESSADBTEST_MAPPEREVENTQUEUE_H

#include <pthread.h>
#include <queue>
#include <android/log.h>
#include "MapperOutputEvent.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif

#define LOG_TAG "eventqueue"

#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define QUEUE_SIZE 10


typedef void (*fpProcessBack)(OutputEvent* event);

class MapperEventQueue{
public:
    MapperEventQueue();
    ~MapperEventQueue();
    int init();
    void enqueueEvent(OutputEvent* event);
    void setProcessCallback(fpProcessBack callback);
    void processEvents();

private:
    OutputEvent* dequeueEventLocked();

private:
    int start;
    int end;
    bool full;
    OutputEvent* mQueue[QUEUE_SIZE];
    pthread_t mThreadId;
    pthread_mutex_t mMutex;
    pthread_cond_t mCond;
    fpProcessBack mCallback;
};

#endif //WIRELESSADBTEST_MAPPEREVENTQUEUE_H
