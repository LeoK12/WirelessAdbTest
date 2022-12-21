//
// Created by makx on 2022/12/21.
//

#ifndef WIRELESSADBTEST_INPUTMAPPER_H
#define WIRELESSADBTEST_INPUTMAPPER_H

#include <linux/input.h>
#include "InputDevice.h"

enum {
    DISPLAY_ORIENTATION_0 = 0,
    DISPLAY_ORIENTATION_90 = 1,
    DISPLAY_ORIENTATION_180 = 2,
    DISPLAY_ORIENTATION_270 = 3
};

class InputDeviceContext;

class InputMapper{
public:
    explicit InputMapper(InputDeviceContext& context);
    virtual ~InputMapper();

    InputDeviceContext& getContext(){return mContext;}

    virtual void configure();
    virtual void reset();
    virtual void process(struct input_event* event) = 0;
    virtual void setDisplayInfo(int width, int height, int rotation) = 0;

protected:
    InputDeviceContext& mContext;
};

#endif //WIRELESSADBTEST_INPUTMAPPER_H
