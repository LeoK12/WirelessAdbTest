//
// Created by makx on 2023/1/3.
//

#include <memory>
#include "InputReader.h"


int openDeviceCallback(void* p, char* path){
    InputReader* reader = (InputReader*)p;
    return reader->openDevice(path);
}

int closeDeviceCallback(void* p, char* path){
    InputReader* reader = (InputReader*)p;
    return reader->closeDevice(path);
}

void processEventCallback(void* p, int fd, struct input_event* events, size_t count){
    InputReader* reader = (InputReader*)p;
    reader->processEvent(fd, events, count);
}

void updateGrabStateCallback(void* p, bool enable){
    InputReader* reader = (InputReader*)p;
    reader->updateGrabState(enable);
}

InputReader::InputReader(std::shared_ptr<EventHub> eventHub, MapperEventQueue* queue):
    mEventHub(eventHub), mQueue(queue), mNextDeviceId(0),
    mDisplayWidth(0), mDisplayHeight(0), mDisplayRotation(0){
    EventHubPlugin* plugin = new EventHubPlugin();
    plugin->p = this;
    plugin->openDevice = openDeviceCallback;
    plugin->closeDevice = closeDeviceCallback;
    plugin->processEvent = processEventCallback;
    plugin->updateGrabState = updateGrabStateCallback;
    mEventHub->setPlugin(plugin);
}

InputReader::~InputReader(){

}

int InputReader::openDevice(char* path){
    ALOGE("[%s:%d]Path = %s", __func__, __LINE__, path);
    int fd = open(path,  O_RDONLY | O_CLOEXEC);
    if (fd < 0){
        ALOGE("could not open %s, %s\n", path, strerror(errno));
        return -errno;
    }

    InputDeviceIdentifier identifier;

    char buffer[80];
    if (ioctl(fd, EVIOCGNAME(sizeof(buffer) - 1), &buffer) < 1){
        ALOGE("Could not get device name for %s: %s", path, strerror(errno));
        close(fd);
        return -1;
    }

    buffer[sizeof(buffer) - 1] = '\0';
    identifier.name = buffer;

    int driver_version;
    if (ioctl(fd, EVIOCGVERSION, &driver_version)) {
        ALOGE("could not get driver version for %s, %s", path, strerror(errno));
        close(fd);
        return -1;
    }

    struct input_id input_id;
    if (ioctl(fd, EVIOCGID, &input_id)){
        ALOGE("can not get device input id %s, %s\n", path, strerror(errno));
        close(fd);
        return -1;
    }

    identifier.bus = input_id.bustype;
    identifier.product = input_id.product;
    identifier.vendor = input_id.vendor;
    identifier.version = input_id.version;

    if (ioctl(fd, EVIOCGPHYS(sizeof(buffer) - 1), &buffer) >= 1) {
        buffer[sizeof(buffer) - 1] = '\0';
        identifier.location = buffer;
    }

    if (ioctl(fd, EVIOCGUNIQ(sizeof(buffer) - 1), &buffer) >= 1) {
        buffer[sizeof(buffer) - 1] = '\0';
        identifier.uniqueId = buffer;
    }

    int32_t deviceId = mNextDeviceId++;
    std::unique_ptr<InputDevice> device = std::make_unique<InputDevice>(fd, deviceId, path, identifier);

    device->readDeviceBitMask();

    bool haveKeyboardKeys =
        device->keyBitmask.any(0, BTN_MISC) || device->keyBitmask.any(BTN_WHEEL, KEY_MAX + 1);
    bool haveGamepadButtons = device->keyBitmask.any(BTN_MISC, BTN_MOUSE) ||
        device->keyBitmask.any(BTN_JOYSTICK, BTN_DIGI);
    if (haveKeyboardKeys || haveGamepadButtons) {
        device->classes |= KEYBOARD;
    }

    // See if this is a cursor device such as a trackball or mouse.
    if (device->keyBitmask.test(BTN_MOUSE) && device->relBitmask.test(REL_X) &&
            device->relBitmask.test(REL_Y)) {
        device->classes |= CURSOR;
    }

    // See if this is a touch pad.
    // Is this a new modern multi-touch driver?
    if (device->absBitmask.test(ABS_MT_POSITION_X) && device->absBitmask.test(ABS_MT_POSITION_Y)) {
        // Some joysticks such as the PS3 controller report axes that conflict
        // with the ABS_MT range.  Try to confirm that the device really is
        // a touch screen.
        if (device->keyBitmask.test(BTN_TOUCH) || !haveGamepadButtons) {
            device->classes |= (TOUCH | TOUCH_MT);
        }
        // Is this an old style single-touch driver?
    } else if (device->keyBitmask.test(BTN_TOUCH) && device->absBitmask.test(ABS_X) &&
            device->absBitmask.test(ABS_Y)) {
        device->classes |= TOUCH;
        // Is this a BT stylus?
    } else if ((device->absBitmask.test(ABS_PRESSURE) || device->keyBitmask.test(BTN_TOUCH)) &&
            !device->absBitmask.test(ABS_X) && !device->absBitmask.test(ABS_Y)) {
        device->classes |= EXTERNAL_STYLUS;
        // Keyboard will try to claim some of the buttons but we really want to reserve those so we
        // can fuse it with the touch screen data, so just take them back. Note this means an
        // external stylus cannot also be a keyboard device.
        device->classes &= ~KEYBOARD;
    }

    // See if this device is a joystick.
    // Assumes that joysticks always have gamepad buttons in order to distinguish them
    // from other devices such as accelerometers that also have absolute axes.
    if (haveGamepadButtons) {
        auto assumedClasses = device->classes | JOYSTICK;
        for (int i = 0; i <= ABS_MAX; i++) {
            if (device->absBitmask.test(i) &&
                    (device->getAbsAxisUsage(i, assumedClasses) & JOYSTICK)) {
                device->classes = assumedClasses;
                break;
            }
        }
    }

    // Check whether this device is an accelerometer.
    if (device->propBitmask.test(INPUT_PROP_ACCELEROMETER)) {
        device->classes |= SENSOR;
    }

    // Check whether this device has switches.
    for (int i = 0; i <= SW_MAX; i++) {
        if (device->swBitmask.test(i)) {
            device->classes |= SWITCH;
            break;
        }
    }

    // Check whether this device supports the vibrator.
    if (device->ffBitmask.test(FF_RUMBLE)) {
        device->classes |= VIBRATOR;
    }

    // If the device isn't recognized as something we handle, don't monitor it.
    if (device->classes == 0) {
        ALOGE("Dropping device: id=%d, path='%s', name='%s'", deviceId, path,
                device->identifier.name.c_str());
        close(fd);
        return -errno;
    }

    device->addEventHubDevice(mQueue);
    device->setDisplayInfo(mDisplayWidth, mDisplayHeight, mDisplayRotation);
    device->configure();
    device->reset();

    if (mIsGrabEnabled){
        if (ioctl(fd, EVIOCGRAB, (void*)1)){
            ALOGE("can NOT grab selected input device. %s", strerror(errno));
            close(fd);
            return -errno;
        }
    }

    if (mEventHub->registerDeviceForEpoll(*device)){
        close(fd);
        return -errno;
    }

    ALOGE("New Device: id = %d, fd = %d, path = '%s', name = '%s', classes = '%x'",
            device->id, device->fd, path, identifier.name.c_str(), device->classes);

    mDevices.emplace(device->id, std::move(device));
    return 0;
}

int InputReader::closeDevice(char* path){
    auto deviceIt = std::find_if(mDevices.begin(), mDevices.end(), [path](auto& devicePair){
        return strcmp(devicePair.second->path.c_str(), path);
    });

    if (deviceIt == mDevices.end()){
        return 0;
    }

    std::shared_ptr<InputDevice> device = std::move(deviceIt->second);
    mEventHub->unregisterDeviceForEpoll(*device);
    device->removeEventHubDevice();
    mDevices.erase(deviceIt);
    return 0;
}

void InputReader::processEvent(int fd, struct input_event* events, size_t count){
    if (!mIsGrabEnabled){
        return;
    }

    auto deviceIt = std::find_if(mDevices.begin(), mDevices.end(), [fd](auto& devicePair){
        return devicePair.second->fd == fd;
    });

    if (deviceIt == mDevices.end()){
        ALOGE("Failed to find input device");
        return;
    }

    InputDevice* device = deviceIt->second.get();
    device->process(events, count);
}

void InputReader::updateGrabState(bool enable){
    if (mIsGrabEnabled == enable){
        return;
    }

    ALOGE("UpdateGrabState : enable = %d\n", enable);
    for (auto& deviceIt : mDevices){
        InputDevice* device = deviceIt.second.get();
        ALOGE("UpdateGrabState : path = %s\n", device->path.c_str());
        if (ioctl(device->fd, EVIOCGRAB, enable ? (void*)1 : (void*)0)){
            ALOGE("can NOT grab selected input device. %s", strerror(errno));
            return;
        }
    }

    mIsGrabEnabled = enable;
}

int InputReader::start(){
    mEventHub->initEpoll();
    return 0;
}

int InputReader::stop(){
    return 0;
}

void InputReader::updateDisplayInfo(int width, int height, int rotation){
    mDisplayWidth = width;
    mDisplayHeight = height;
    mDisplayRotation = rotation;
    for (auto& deviceIt : mDevices){
        std::shared_ptr<InputDevice> device = std::move(deviceIt.second);
        device->setDisplayInfo(width, height, rotation);
    }
}
