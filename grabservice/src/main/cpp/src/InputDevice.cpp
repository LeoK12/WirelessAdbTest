//
// Created by makx on 2023/1/3.
//

#include <errno.h>
#include <memory>
#include "MultiTouchInputMapper.h"
#include "KeyboardInputMapper.h"
#include "InputDevice.h"

InputDevice::InputDevice(int fd, int32_t id, char *path, InputDeviceIdentifier &identifier):
        fd(fd), id(id), path(path), identifier(identifier), classes(0), mapper_size(0){
            memset(mappers, 0, sizeof(mappers));
}

InputDevice::~InputDevice(){
    readDeviceBitMask(EVIOCGPROP(0), propBitmask);
}

template <std::size_t N>
int InputDevice::readDeviceBitMask(unsigned long ioctlCode, BitArray<N>& bitArray){
    if(fd < 0){
        return -1;
    }

    if ((_IOC_SIZE(ioctlCode) == 0)) {
        ioctlCode |= _IOC(0, 0, 0, bitArray.bytes());
    }

    typename BitArray<N>::Buffer buffer;
    int ret = ioctl(fd, ioctlCode, buffer.data());
    bitArray.loadFromBuffer(buffer);
    return ret;
}

int InputDevice::readDeviceBitMask(){
    readDeviceBitMask(EVIOCGBIT(EV_KEY, 0), keyBitmask);
    readDeviceBitMask(EVIOCGBIT(EV_ABS, 0), absBitmask);
    readDeviceBitMask(EVIOCGBIT(EV_REL, 0), relBitmask);
    readDeviceBitMask(EVIOCGBIT(EV_SW, 0), swBitmask);
    readDeviceBitMask(EVIOCGBIT(EV_LED, 0),ledBitmask);
    readDeviceBitMask(EVIOCGBIT(EV_FF, 0), ffBitmask);
    readDeviceBitMask(EVIOCGBIT(EV_MSC, 0),mscBitmask);
    readDeviceBitMask(EVIOCGPROP(0), propBitmask);
    return 0;
}

uint32_t InputDevice::getAbsAxisUsage(int32_t axis, uint32_t classes){
    if (classes & TOUCH){
        switch(axis){
            case ABS_X:
            case ABS_Y:
            case ABS_PRESSURE:
            case ABS_TOOL_WIDTH:
            case ABS_DISTANCE:
            case ABS_TILT_X:
            case ABS_TILT_Y:
            case ABS_MT_SLOT:
            case ABS_MT_TOUCH_MAJOR:
            case ABS_MT_TOUCH_MINOR:
            case ABS_MT_WIDTH_MAJOR:
            case ABS_MT_WIDTH_MINOR:
            case ABS_MT_ORIENTATION:
            case ABS_MT_POSITION_X:
            case ABS_MT_POSITION_Y:
            case ABS_MT_TOOL_TYPE:
            case ABS_MT_BLOB_ID:
            case ABS_MT_TRACKING_ID:
            case ABS_MT_PRESSURE:
            case ABS_MT_DISTANCE:
                return TOUCH;
        }
    }

    if (classes & SENSOR){
        switch (axis) {
            case ABS_X:
            case ABS_Y:
            case ABS_Z:
            case ABS_RX:
            case ABS_RY:
            case ABS_RZ:
                return SENSOR;
        }
    }

    if (classes & EXTERNAL_STYLUS){
        if (axis == ABS_PRESSURE){
            return EXTERNAL_STYLUS;
        }
    }

    return classes & JOYSTICK;
}

void InputDevice::addEventHubDevice(MapperEventQueue* queue){
    InputDeviceContext* contextPtr = new InputDeviceContext(*this, queue);

    if (classes & TOUCH_MT){
        mappers[mapper_size++] = new MultiTouchInputMapper(*contextPtr);
    }

    if (classes & KEYBOARD){
        mappers[mapper_size++] = new KeyboardInputMapper(*contextPtr);
    }
}

void InputDevice::removeEventHubDevice(){

}

void InputDevice::configure(){
    for (int i = 0; i < mapper_size; i++){
        InputMapper* mapper = mappers[i];
        mapper->configure();
    }
}

void InputDevice::reset(){
    for (int i = 0; i < mapper_size; i++){
        InputMapper* mapper = mappers[i];
        mapper->reset();
    }
}

void InputDevice::process(struct input_event* events, size_t count){
    for (size_t index = 0; index < count; index++){ 
        struct input_event* event = events + index;
        if (event->type == EV_SYN && event->code == SYN_DROPPED){
            reset();
            continue;
        }

        for (size_t i = 0; i < mapper_size; i++){
            InputMapper* mapper = *(mappers+i);
            mapper->process(event);
        }
    }
}

void InputDevice::setDisplayInfo(int width, int height, int rotation){
    for (int i = 0; i < mapper_size; i++){
        InputMapper* mapper = mappers[i];
        mapper->setDisplayInfo(width, height, rotation);
    }
}

InputDeviceContext::InputDeviceContext(InputDevice& device, MapperEventQueue* queue):
        mDevice(device), mQueue(queue){

}

InputDeviceContext::~InputDeviceContext(){
    ALOGE("free InputDeviceContext");
}
