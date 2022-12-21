//
// Created by makx on 2023/2/8.
//
#include "KeyboardInputMapper.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif

#define LOG_TAG "keyboard_input_mapper"

#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


KeyboardInputMapper::KeyboardInputMapper(InputDeviceContext& context):
    InputMapper(context){
}

KeyboardInputMapper::~KeyboardInputMapper(){}


void KeyboardInputMapper::configure(){

}

void KeyboardInputMapper::reset(){

}

void KeyboardInputMapper::processKey(int keyCode, bool down){
    OutputKeyEvent* event = (OutputKeyEvent*)malloc(sizeof(OutputKeyEvent));
    if (event == nullptr){
        ALOGE("failed to allocate OutputKeyEvent");
        return;
    }

    event->head.deviceId = mContext.getInputDeviceId();
    event->head.type = OUTPUT_KEY_EVENT;
    event->keyCode = keyCode;
    event->action = down ? AKEY_EVENT_ACTION_DOWN : AKEY_EVENT_ACTION_UP;
    mContext.getQueue()->enqueueEvent(&event->head);
}

void KeyboardInputMapper::process(struct input_event* event){
    if (event->type == EV_KEY){
        processKey(event->code, event->value != 0);
    }
}

void KeyboardInputMapper::setDisplayInfo(int width, int height, int rotation){

}
