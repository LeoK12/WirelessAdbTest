//
// Created by makx on 2023/1/31.
//

#include "MapperEventQueue.h"

MapperEventQueue::MapperEventQueue():start(0), end(0), full(false){
    memset(mQueue, 0, sizeof(mQueue));
}

MapperEventQueue::~MapperEventQueue(){
    pthread_mutex_destroy(&mMutex);
    pthread_cond_destroy(&mCond);
}

void* process_queue(void* p){
    MapperEventQueue* queue = (MapperEventQueue*)p;
    queue->processEvents();
    return 0;
}

int MapperEventQueue::init(){
    pthread_mutex_init(&mMutex, nullptr);
    pthread_cond_init(&mCond, nullptr);
    return pthread_create(&mThreadId, nullptr, process_queue, this);
}

void MapperEventQueue::enqueueEvent(OutputEvent* event){
    pthread_mutex_lock(&mMutex);
    if (full){
        ALOGE("queue is full");
        pthread_mutex_unlock(&mMutex);
        return;
    }

    mQueue[start++] = event;
    start %= QUEUE_SIZE;
    if (start == end){
        full = true;
    }
    pthread_cond_signal(&mCond);
    pthread_mutex_unlock(&mMutex);
}

OutputEvent* MapperEventQueue::dequeueEventLocked(){
    OutputEvent* event = nullptr;
    if (end == start){
        ALOGE("queue is empty");
        return event;
    }

    event = mQueue[end++];
    end %= QUEUE_SIZE;
    if (full){
        full = false;
    }
    return event;
}

void MapperEventQueue::processEvents(){
    while(true){
        OutputEvent* event = nullptr;
        pthread_mutex_lock(&mMutex);
        event = dequeueEventLocked();
        pthread_mutex_unlock(&mMutex);
        if (event == nullptr){
            pthread_mutex_lock(&mMutex);
            pthread_cond_wait(&mCond, &mMutex);
            event = dequeueEventLocked();
            pthread_mutex_unlock(&mMutex);
        }

        if (event == nullptr){
            continue;
        }

        if (mCallback == nullptr){
            break;
        }

        mCallback(event);
        delete event;
    }
}

void MapperEventQueue::setProcessCallback(fpProcessBack callback){
    mCallback = callback;
}
