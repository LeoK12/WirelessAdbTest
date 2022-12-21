//
// Created by makx on 2023/2/8.
//

#ifndef WIRELESSADBTEST_KEYBOARDINPUTMAPPER_H
#define WIRELESSADBTEST_KEYBOARDINPUTMAPPER_H

#include <android/log.h>
#include "InputDevice.h"



static const int AKEY_EVENT_ACTION_DOWN = 0;
static const int AKEY_EVENT_ACTION_UP   = 1;

class KeyboardInputMapper : public InputMapper{
public:
    explicit KeyboardInputMapper(InputDeviceContext& context);
    virtual ~KeyboardInputMapper();

    void configure() override;
    void reset() override;
    void process(struct input_event* event) override;
    void setDisplayInfo(int width, int height, int rotation) override;

private:
    void processKey(int keyCode, bool down);
};
#endif //WIRELESSADBTEST_KEYBOARDINPUTMAPPER_H
