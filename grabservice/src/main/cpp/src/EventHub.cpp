//
// Created by makx on 2022/12/21.
//

#include "EventHub.h"

EventHub::EventHub(){
    mEpollFd = -1;
    mINotifyFd = -1;
    mInputWd = -1;
    mEnableGrabFd = -1;
    mAppSwitchPath = nullptr;
    mEnableGrabPath = nullptr;
    memset(&mConfigBuffer, 0, sizeof(mConfigBuffer));
}

EventHub::~EventHub(){

}

int EventHub::registerDeviceForEpoll(InputDevice& device){
    return registerFdForEpoll(mEpollFd, device.fd);
}

int EventHub::unregisterDeviceForEpoll(InputDevice& device){
    return unregisterFdForEpoll(mEpollFd, device.fd);
}

int EventHub::openDevice(char* path){
    if (mPlugin == nullptr || mPlugin->openDevice == nullptr || mPlugin->p == nullptr){
        ALOGE("plugin is not ready");
        return 1;
    }

    return mPlugin->openDevice(mPlugin->p, path);
}

int EventHub::closeDevice(char* path){
    if (mPlugin == nullptr || mPlugin->closeDevice == nullptr || mPlugin->p == nullptr){
        ALOGE("plugin is not ready");
        return 1;
    }

    return mPlugin->closeDevice(mPlugin->p, path);
}

int EventHub::processEvent(int fd, struct input_event* events, size_t count){
    if (mPlugin == nullptr || mPlugin->processEvent == nullptr || mPlugin->p == nullptr){
        ALOGE("plugin is not ready");
        return 1;
    }

    mPlugin->processEvent(mPlugin->p, fd, events, count);
    return 0;
}

int EventHub::scanDir(const char* dirname){
    int ret = -1;
    char devname[PATH_MAX];
    struct dirent *de;
    DIR* dir = opendir(dirname);
    if (dir == NULL){
        ALOGE("Could not open dir %s : %s", dirname, strerror(errno));
        return ret;
    }

    strcpy(devname, dirname);
    char *filename = devname + strlen(dirname);
    *filename++ = '/';

    while((de = readdir(dir))){
        if(de->d_name[0] == '.' && (de->d_name[1] == '\0' ||(de->d_name[1] == '.' && de->d_name[2] == '\0'))){
            continue;
        }

        strcpy(filename, de->d_name);
        openDevice(devname);
    }

    return 0;
}

int EventHub::registerFdForEpoll(int epollfd, int fd){
    struct epoll_event eventItem = {};
    eventItem.events = EPOLLIN | EPOLLWAKEUP;
    eventItem.data.fd = fd;
    if (epoll_ctl(epollfd, EPOLL_CTL_ADD, fd, &eventItem)){
        ALOGE("Could not add fd to epoll instance: %s", strerror(errno));
        return -errno;
    }

    return 0;
}

int EventHub::unregisterFdForEpoll(int epollfd, int fd){
    if (epoll_ctl(epollfd, EPOLL_CTL_DEL, fd, nullptr)) {
        ALOGE("Could not remove fd from epoll instance: %s", strerror(errno));
        return -errno;
    }

    return 0;
}

int EventHub::updateGrabStateInternal(bool enable){
    if (mPlugin == nullptr || mPlugin->updateGrabState == nullptr || mPlugin->p == nullptr){
        ALOGE("plugin is not ready");
        return 1;
    }

    mPlugin->updateGrabState(mPlugin->p, enable);
    return 0;
}

int EventHub::updateGrabState(){
    char buffer[1];
    int fd = open(mEnableGrabPath, O_RDONLY|O_CLOEXEC);
    if (fd < 0){
        ALOGE("could not open %s, %s\n", mEnableGrabPath, strerror(errno));
        return -errno;
    }

    int ret = read(fd, buffer, sizeof(buffer));
    if (ret != sizeof(buffer)){
        ALOGE("could not read %s, %s\n", mEnableGrabPath, strerror(errno));
        close(fd);
        return -errno;
    }

    bool enable = buffer[0] == '1';
    updateGrabStateInternal(enable);

    close(fd);
    return 0;
}

int EventHub::readNotify(){
    char devname[PATH_MAX];
    char* filename;
    char event_buf[512];
    int event_size;
    int event_pos = 0;
    struct inotify_event* event;
    int ret = read(mINotifyFd, event_buf, sizeof(event_buf));
    if (ret < (int)sizeof(*event)){
        if (errno == EINTR){
            return 0;
        }

        ALOGE("could not get event. %s\n", strerror(errno));
        return -errno;
    }

    strcpy(devname, DEV_PATH);
    filename = devname + strlen(devname);
    *filename++ = '/';

    while(ret >= (int)sizeof(*event)){
        event = (struct inotify_event*)(event_buf + event_pos);
        if (event->len){
            if (event->wd == mInputWd){
                strcpy(filename, event->name);
                ALOGE("[%s:%d] devname = %s\n", __func__, __LINE__, devname);
                if (event->mask & IN_CREATE){
                    openDevice(devname);
                } else {
                    closeDevice(devname);
                }
            }
        } else {
            if (event->wd == mEnableGrabFd){
                updateGrabState();
            }
        }

        event_size = sizeof(*event) + event->len;
        ret -= event_size;
        event_pos += event_size;
    }

    return 0;
}

int EventHub::readConfig(){
    int fd = open(CONFIG_FILE_NAME, O_RDONLY|O_CLOEXEC);
    if (fd < 0){
        ALOGE("Could not open %s: %s", CONFIG_FILE_NAME, strerror(errno));
        return -errno;
    }

    memset(mConfigBuffer, 0, sizeof(mConfigBuffer));
    int ret = read(fd, mConfigBuffer, sizeof(mConfigBuffer));
    if (ret < 0){
        ALOGE("Failed to read %s: %s", CONFIG_FILE_NAME, strerror(errno));
        return -errno;
    }

    char* target = strstr(mConfigBuffer, ";");
    if (target == NULL){
        close(fd);
        ALOGE("config txt is corrupted");
        return -1;
    }

    *target = 0;
    mAppSwitchPath = mConfigBuffer;
    mEnableGrabPath = target+1;
    mConfigBuffer[ret-1] = 0;

    ALOGE("mAppSwitchPath = %s", mAppSwitchPath);
    ALOGE("mEnableGrabPath = %s", mEnableGrabPath);

    close(fd);
    return 0;
}

static void sig_hup(int /* signum */){
    ALOGE("SIGHUP received");   
    exit(0);
}

int EventHub::initEpoll(){
    if (readConfig()){
        return -1;
    }

    signal(SIGHUP, sig_hup);
    mEpollFd = epoll_create1(EPOLL_CLOEXEC);
    if (mEpollFd < 0){
        ALOGE("Could not create epoll instance: %s", strerror(errno));
        return -errno;
    }

    scanDir(DEV_PATH);

    mINotifyFd = inotify_init();
    mInputWd = inotify_add_watch(mINotifyFd, DEV_PATH, IN_DELETE|IN_CREATE);
    if (mInputWd < 0){
        ALOGE("Could not register INotify for %s: %s", DEV_PATH, strerror(errno));
        return -errno;
    }

    mEnableGrabFd = inotify_add_watch(mINotifyFd, mEnableGrabPath, IN_MODIFY);
    if (mEnableGrabFd < 0){
        ALOGE("Could not register INotify for %s: %s", mEnableGrabPath, strerror(errno));
        return -errno;
    }

    if (registerFdForEpoll(mEpollFd, mINotifyFd)){
        return -errno;
    }

    struct epoll_event events[EPOLL_MAX_EVENTS];
    struct input_event readBuffer[EVENT_BUFFER_SIZE];
    while(true){
        int pollResult = epoll_wait(mEpollFd, events, EPOLL_MAX_EVENTS, -1);
        if (pollResult <= 0 ){
            ALOGE("pollResult = %d", pollResult);
            continue;
        }

        ALOGE("pollResult = %d", pollResult);
        for (int i = 0; i < pollResult; i++){
            struct epoll_event event = events[i];
            if (event.data.fd == mINotifyFd){
                readNotify();
                continue;
            }

            if (event.events & EPOLLIN){
                int32_t readSize = read(event.data.fd, readBuffer, sizeof(struct input_event)*EVENT_BUFFER_SIZE);
                if (readSize ==0 || (readSize < 0 && errno == ENODEV)){
                    ALOGE("could not get event, removed?: %s", strerror(errno));
                    continue;
                } else if  (readSize < 0) {
                    if (errno != EAGAIN && errno != EINTR) {
                        ALOGE("could not get event (errno=%d:%s)", errno, strerror(errno));
                        continue;
                    }
                } else if ((readSize % sizeof(struct input_event)) != 0) {
                    ALOGE("could not get event (wrong size: %d)", readSize);
                    continue;
                }

                if (mPlugin == nullptr || mPlugin->processEvent == nullptr || mPlugin->p == nullptr){
                    continue;
                }

                size_t count = size_t(readSize) / sizeof(struct input_event);
                processEvent(event.data.fd, (struct input_event*)(&readBuffer), count);
            }
        }
    }
}
