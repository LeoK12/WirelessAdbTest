//
// Created by makx on 2022/12/21.
//

#ifndef WIRELESSADBTEST_EVENTHUB_H
#define WIRELESSADBTEST_EVENTHUB_H

#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/inotify.h>
#include <sys/limits.h>
#include <linux/input.h>
#include <sys/epoll.h>
#include <errno.h>
#include <unistd.h>
#include <vector>
#include <bitset>
#include <mutex>
#include <android/log.h>

#include "InputDevice.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif

#define LOG_TAG "eventhub"

#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define DEV_PATH         "/dev/input"
#define CONFIG_FILE_NAME "/data/local/tmp/config.txt"

static const int EPOLL_MAX_EVENTS = 16;
static const int EVENT_BUFFER_SIZE = 256;
static const int CONFIG_BUFFER_SIZE = 256;

struct EventHubPlugin{
    void* p;
    int (*openDevice)(void* p, char* path);
    int (*closeDevice)(void* p, char* path);
    void (*processEvent)(void* p, int fd, struct input_event* events, size_t count);
    void (*updateGrabState)(void* p, bool enable);
};

class EventHub{
public:
    EventHub();
    ~EventHub();

    void setPlugin(EventHubPlugin* plugin){
        mPlugin = plugin;
    }

    int initEpoll();
    int registerDeviceForEpoll(InputDevice& device);
    int unregisterDeviceForEpoll(InputDevice& device);
    char* getAppSwitchPath(){return mAppSwitchPath;}

private:
    int openDevice(char* path);
    int closeDevice(char* path);
    int processEvent(int fd, struct input_event* event, size_t count);
    int scanDir(const char* dirname);
    int registerFdForEpoll(int epollfd, int fd);
    int unregisterFdForEpoll(int epollfd, int fd);
    int updateGrabStateInternal(bool enable);
    int updateGrabState();
    int readNotify();
    int readConfig();

private:
    int mEpollFd;
    int mINotifyFd;
    int mInputWd;
    int mEnableGrabFd;
    char* mAppSwitchPath;
    char* mEnableGrabPath;
    char mConfigBuffer[CONFIG_BUFFER_SIZE];
    EventHubPlugin* mPlugin;
};
#endif //WIRELESSADBTEST_EVENTHUB_H
