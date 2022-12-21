//
// Created by makx on 2023/1/3.
//

#include <errno.h>
#include "MultiTouchInputMapper.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif

#define LOG_TAG "multi_touch_input_mapper"

#if 1
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define ALOGE(...) 
#endif


MultiTouchInputMapper::MultiTouchInputMapper(InputDeviceContext& context) :
        InputMapper(context),
        mDisplayWidth(0), mDisplayHeight(0), mDisplayRotation(0), mXScale{1.0f}, mYScale(1.0f) {
}

MultiTouchInputMapper::~MultiTouchInputMapper(){

}

int MultiTouchInputMapper::getAbsAxisInfo(int axis, struct AbsAxisInfo* axisInfo){
    int fd = mContext.getInputDeviceFd();
    struct input_absinfo info;
    if (ioctl(fd, EVIOCGABS(axis), &info)){
        ALOGE("could not get axis info %s\n", strerror(errno));
        return -errno;
    }

    if (info.minimum != info.maximum) {
        axisInfo->valid = true;
        axisInfo->minValue = info.minimum;
        axisInfo->maxValue = info.maximum;
        axisInfo->flat = info.flat;
        axisInfo->fuzz = info.fuzz;
        axisInfo->resolution = info.resolution;
    }
    return 0;
}

void MultiTouchInputMapper::configure(){
    getAbsAxisInfo(ABS_MT_POSITION_X, &axes.x);
    getAbsAxisInfo(ABS_MT_POSITION_Y, &axes.y);
    getAbsAxisInfo(ABS_MT_TRACKING_ID, &axes.trackingId);
    getAbsAxisInfo(ABS_MT_SLOT, &axes.slot);
    getAbsAxisInfo(ABS_MT_TOUCH_MAJOR, &axes.touchMajor);
    getAbsAxisInfo(ABS_MT_TOUCH_MINOR, &axes.touchMinor);
    getAbsAxisInfo(ABS_MT_WIDTH_MAJOR, &axes.widthMajor);
    getAbsAxisInfo(ABS_MT_WIDTH_MINOR, &axes.widthMinor);
    getAbsAxisInfo(ABS_MT_ORIENTATION, &axes.orientation);
    getAbsAxisInfo(ABS_MT_PRESSURE, &axes.pressure);
    getAbsAxisInfo(ABS_MT_DISTANCE, &axes.distance);

    if (axes.slot.valid && axes.slot.minValue == 0 && axes.slot.maxValue > 0){
        size_t slotCount = axes.slot.maxValue + 1;
        accumultor.slots = (MultiTouchMotionAccumulator::Slot*)calloc(sizeof(MultiTouchMotionAccumulator::Slot), slotCount);
        if (accumultor.slots == NULL){
            ALOGE("failed to alloc slot\n");
            return;
        }

        accumultor.slotCount = slotCount;
        accumultor.currentSlot = 0;
    }

    int32_t rawWidth = axes.x.maxValue - axes.x.minValue + 1;
    int32_t rawHeight = axes.y.maxValue - axes.y.minValue + 1;
    mXScale = float(mDisplayWidth)/rawWidth;
    mYScale = float(mDisplayHeight)/rawHeight;
}

void MultiTouchInputMapper::reset(){

}

static inline uint32_t clearFirstMarkedBit(BitSet32& bitset){
    uint32_t i = 0;
    for (;i<bitset.size();i++){
        if (!bitset.test(i)){
            continue;
        }

        bitset.reset(i);
        break;
    }

    if (i == bitset.size()){
        return -1;
    }

    return i;
}

static inline uint32_t markFirstUnmarkedBit(BitSet32& bitset){
    uint32_t i = 0;
    for (;i<bitset.size();i++){
        if (bitset.test(i)){
            continue;
        }

        bitset.set(i);
        break;
    }

    if (i == bitset.size()){
        return -1;
    }

    return i;
}

int MultiTouchInputMapper::getEventAction(int action, int changeId, BitSet32 IdBits, RawState* state, std::vector<RawState::Pointer>* pointers){
    uint32_t pointerCount = 0;
    while(!IdBits.none()){
        uint32_t id = clearFirstMarkedBit(IdBits);
        uint32_t index = state->idToIndex[id];
        ALOGE("[%s:%d] id = %d\n", __func__, __LINE__, id);
        ALOGE("[%s:%d] index = %d\n", __func__, __LINE__, index);
        RawState::Pointer pointer;
        memcpy(&pointer, &state->pointers[index], sizeof(pointer));
        pointers->push_back(pointer);

        ALOGE("[%s:%d] pointers.size() = %u\n", __func__, __LINE__, pointers->size());
        if (changeId >= 0 && id == uint32_t(changeId)){
            action |= pointerCount << AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT;
        }

        pointerCount++;
    }

    ALOGE("[%s:%d] changeId = %d\n", __func__, __LINE__, changeId);
    ALOGE("[%s:%d] pointerCount = %u\n", __func__, __LINE__, pointerCount);
    if (changeId >= 0 && pointerCount == 1){
        if (action == AMOTION_EVENT_ACTION_POINTER_DOWN){
            action = AMOTION_EVENT_ACTION_DOWN;
        } else if (action == AMOTION_EVENT_ACTION_POINTER_UP){
            action = AMOTION_EVENT_ACTION_UP;
        }
    }

    return action;
}

void MultiTouchInputMapper::enqueueOutputEvent(int deviceId, int action,
        std::vector<RawState::Pointer> pointers){
    OutputMotionEvent* event = (OutputMotionEvent*)malloc(sizeof(OutputMotionEvent));
    if (event == nullptr){
        ALOGE("failed to allocate OutputMotionEvent");
        return;
    }

    memset(event, 0, sizeof(OutputMotionEvent));
    event->head.deviceId = deviceId;
    event->head.type = OUTPUT_MOTION_EVENT;
    event->action = action;
    event->pointerCount = pointers.size();
    memcpy(event->pointers, pointers.data(), sizeof(RawState::Pointer)*pointers.size());
    mContext.getQueue()->enqueueEvent(&event->head);
}

void MultiTouchInputMapper::dispatchTouches(){
    BitSet32 currentIdBits = currentState.touchIdBits;
    BitSet32 lastIdBits = lastState.touchIdBits;
    std::vector<RawState::Pointer> pointers;

    if (currentIdBits == lastIdBits){
        pointers.clear();
        int action = getEventAction(AMOTION_EVENT_ACTION_MOVE, -1, currentIdBits, &currentState, &pointers);
        enqueueOutputEvent(mContext.getInputDeviceId(), action, pointers);
    } else {
        BitSet32 downIdBits(currentIdBits & ~lastIdBits);
        BitSet32 upIdBits(lastIdBits & ~currentIdBits);
        BitSet32 dispatchedIdBits(lastIdBits);

        while(!upIdBits.none()){
            pointers.clear();
            uint32_t upId = clearFirstMarkedBit(upIdBits);
            int action = getEventAction(AMOTION_EVENT_ACTION_POINTER_UP, upId, dispatchedIdBits, &lastState, &pointers);
            ALOGE("[%s:%d]", __func__, __LINE__);
            enqueueOutputEvent(mContext.getInputDeviceId(), action, pointers);
            dispatchedIdBits.reset(upId);
        }

        while(!downIdBits.none()){
            pointers.clear();
            uint32_t downId = clearFirstMarkedBit(downIdBits);
            dispatchedIdBits.set(downId);
            int action = getEventAction(AMOTION_EVENT_ACTION_POINTER_DOWN, downId, dispatchedIdBits, &currentState, &pointers);
            enqueueOutputEvent(mContext.getInputDeviceId(), action, pointers);
        }
    }

    memcpy(&lastState, &currentState, sizeof(currentState));
}

void MultiTouchInputMapper::rotationTouches(){
    uint32_t pointerCount = currentState.pointerCount;
    {
        for (uint32_t i = 0; i < pointerCount; i++){
            RawState::Pointer* pointer = &currentState.pointers[i];
            int32_t xTransformed = pointer->x;
            int32_t yTransformed = pointer->y;
            switch(mDisplayRotation){
                case DISPLAY_ORIENTATION_90:
                    pointer->x = (yTransformed - axes.y.minValue) * mYScale;
                    pointer->y = (axes.x.maxValue - xTransformed) * mXScale;
                    break;
                case DISPLAY_ORIENTATION_180:
                    pointer->x = (axes.x.maxValue - xTransformed) * mXScale;
                    pointer->y = (axes.y.maxValue - yTransformed) * mYScale;
                    break;
                case DISPLAY_ORIENTATION_270:
                    pointer->x = (axes.y.maxValue - yTransformed) * mYScale;
                    pointer->y = (xTransformed - axes.x.minValue) * mXScale;
                    break;
                default:
                    pointer->x = (xTransformed - axes.x.minValue) * mXScale;
                    pointer->y = (yTransformed - axes.y.minValue) * mYScale;
                    break;
            }
        }
    }
}

void MultiTouchInputMapper::syncEvent(){
    size_t inCount = accumultor.slotCount;
    size_t outCount = 0;
    BitSet32 newPointerIdBits;
    for (size_t inIndex = 0; inIndex < inCount; inIndex++){
        MultiTouchMotionAccumulator::Slot inSlot = accumultor.slots[inIndex];
        if (!inSlot.inUse){
            continue;
        }

        RawState::Pointer* pointer = &currentState.pointers[outCount];
        pointer->x = inSlot.x;
        pointer->y = inSlot.y;
        pointer->orientation = inSlot.orientation;
        pointer->pressure = inSlot.pressure;
        pointer->distance = inSlot.distance;
        pointer->tooltype = inSlot.tooltype;

        if (axes.touchMajor.valid && axes.widthMajor.valid){
            pointer->touchMajor = inSlot.touchMajor;
            pointer->touchMinor = inSlot.touchMinor;
            pointer->widthMajor = inSlot.widthMajor;
            pointer->widthMinor = inSlot.widthMinor;
        } else if (axes.touchMajor.valid){
            pointer->touchMajor = inSlot.touchMajor;
            pointer->touchMinor = inSlot.touchMinor;
            pointer->widthMajor = inSlot.touchMajor;
            pointer->widthMinor = inSlot.touchMinor;
        } else if (axes.widthMajor.valid){
            pointer->touchMajor = inSlot.widthMajor;
            pointer->touchMinor = inSlot.widthMinor;
            pointer->widthMajor = inSlot.widthMajor;
            pointer->widthMinor = inSlot.widthMinor;
        }

        int32_t trackingId = inSlot.trackingId;
        int32_t id = -1;
        if (trackingId >= 0){
            for (BitSet32 idBits(mPointerIdBits); !idBits.none();){
                uint32_t n = clearFirstMarkedBit(idBits);
                if (mPointerTrackingIdMap[n] == trackingId){
                    id = n;
                }
            }

            if (id < 0 && !mPointerIdBits.all()){
                id = markFirstUnmarkedBit(mPointerIdBits);
                mPointerTrackingIdMap[id] = trackingId;
            }
        }

        if (id >= 0){
            pointer->id = id;
            currentState.idToIndex[id] = outCount;
            currentState.touchIdBits.set(id);
            newPointerIdBits.set(id);
        } else {
            newPointerIdBits.reset();
        }

        ALOGE("[%s:%d] id = %d\n", __func__, __LINE__, pointer->id);
        ALOGE("[%s:%d] x = %d\n", __func__, __LINE__, pointer->x);
        ALOGE("[%s:%d] y = %d\n", __func__, __LINE__, pointer->y);

        outCount++;
    }

    currentState.pointerCount = outCount;
    currentState.touchIdBits = newPointerIdBits;
    mPointerIdBits = newPointerIdBits;
}

void MultiTouchInputMapper::sync(){
    syncEvent();
    rotationTouches();
    dispatchTouches();
}

void MultiTouchInputMapper::process(struct input_event* event){
    if (event->type == EV_ABS){
        if (event->code == ABS_MT_SLOT) {
            accumultor.currentSlot = event->value;
        }

        if (accumultor.currentSlot < 0 || accumultor.currentSlot >= accumultor.slotCount){
            ALOGE("currentSlot should be between 0 and %zu\n", accumultor.slotCount);
            return;
        }

        MultiTouchMotionAccumulator::Slot* slot = &accumultor.slots[accumultor.currentSlot];
        switch(event->code){
            case ABS_MT_POSITION_X:
                slot->x = event->value;
                break;
            case ABS_MT_POSITION_Y:
                slot->y = event->value;
                break;
            case ABS_MT_TRACKING_ID:
                if (event->value < 0){
                    slot->inUse = false;
                } else {
                    slot->inUse = true;
                    slot->trackingId = event->value;
                }
                break;
            case ABS_MT_TOUCH_MAJOR:
                slot->touchMajor = event->value;
                break;
            case ABS_MT_TOUCH_MINOR:
                slot->touchMinor = event->value;
                break;
            case ABS_MT_WIDTH_MAJOR:
                slot->widthMajor = event->value;
                break;
            case ABS_MT_WIDTH_MINOR:
                slot->widthMinor = event->value;
                break;
            case ABS_MT_ORIENTATION:
                slot->orientation = event->value;
                break;
            case ABS_MT_PRESSURE:
                slot->pressure = event->value;
                break;
            case ABS_MT_DISTANCE:
                slot->distance = event->value;
                break;
            case ABS_MT_TOOL_TYPE:
                slot->tooltype = event->value;
                break;
            default:
                break;
        }
    }

    if (event->type == EV_SYN && event->code == SYN_REPORT){
        sync();
    }
}

void MultiTouchInputMapper::setDisplayInfo(int width, int height, int rotation){
    ALOGE("[%s:%d]width = %d, height = %d, rotation = %d", __func__, __LINE__, width, height, rotation);
    mDisplayRotation = rotation;
    if (rotation == DISPLAY_ORIENTATION_90 || rotation == DISPLAY_ORIENTATION_270){
        mDisplayWidth = height;
        mDisplayHeight = width;
    } else {
        mDisplayWidth = width;
        mDisplayHeight = height;
    }

    int32_t rawWidth = axes.x.maxValue - axes.x.minValue + 1;
    int32_t rawHeight = axes.y.maxValue - axes.y.minValue + 1;
    mXScale = float(mDisplayWidth)/rawWidth;
    mYScale = float(mDisplayHeight)/rawHeight;
}
