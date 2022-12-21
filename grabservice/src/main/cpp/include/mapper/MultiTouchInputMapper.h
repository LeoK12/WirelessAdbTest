//
// Created by makx on 2022/12/21.
//

#ifndef WIRELESSADBTEST_MULTITOUCHINPUTMAPPER_H
#define WIRELESSADBTEST_MULTITOUCHINPUTMAPPER_H

#include <vector>
#include <bitset>
#include <android/log.h>
#include "InputDevice.h"

typedef std::bitset<32> BitSet32;

static const int MAX_POINTERS = 16;
static const int MAX_POINTER_ID = 31;

static const int AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT = 8;
static const int AMOTION_EVENT_ACTION_MASK = 0xff;
static const int AMOTION_EVENT_ACTION_DOWN = 0;
static const int AMOTION_EVENT_ACTION_UP = 1;
static const int AMOTION_EVENT_ACTION_MOVE = 2;
static const int AMOTION_EVENT_ACTION_POINTER_DOWN = 5;
static const int AMOTION_EVENT_ACTION_POINTER_UP = 6;

struct AbsAxisInfo {
    bool valid;
    int32_t minValue;
    int32_t maxValue;
    int32_t flat;
    int32_t fuzz;
    int32_t resolution;
};

struct PointerAxes {
    AbsAxisInfo slot;
    AbsAxisInfo trackingId;
    AbsAxisInfo x;
    AbsAxisInfo y;
    AbsAxisInfo touchMajor;
    AbsAxisInfo touchMinor;
    AbsAxisInfo widthMajor;
    AbsAxisInfo widthMinor;
    AbsAxisInfo orientation;
    AbsAxisInfo pressure;
    AbsAxisInfo distance;
    AbsAxisInfo tooltype;
};

struct MultiTouchMotionAccumulator {
    struct Slot {
        bool inUse;
        int32_t x;
        int32_t y;
        int32_t trackingId;
        int32_t touchMajor;
        int32_t touchMinor;
        int32_t widthMajor;
        int32_t widthMinor;
        int32_t orientation;
        int32_t pressure;
        int32_t distance;
        int32_t tooltype;
    };

    int32_t currentSlot;
    Slot* slots;
    size_t slotCount;
};

struct RawState {
    struct Pointer {
        uint32_t id;
        int32_t x;
        int32_t y;
        int32_t touchMajor;
        int32_t touchMinor;
        int32_t widthMajor;
        int32_t widthMinor;
        int32_t orientation;
        int32_t pressure;
        int32_t distance;
        int32_t tooltype;
    };

    uint32_t pointerCount;
    Pointer pointers[MAX_POINTERS];
    BitSet32 touchIdBits;
    uint32_t idToIndex[MAX_POINTER_ID + 1];
};

class MultiTouchInputMapper : public InputMapper {
public:
    explicit MultiTouchInputMapper(InputDeviceContext& context);
    ~MultiTouchInputMapper() override;

    void configure() override;
    void reset() override;
    void process(struct input_event* event) override;
    void setDisplayInfo(int width, int height, int rotation) override;

private:
    void enqueueOutputEvent(int deviceId, int action, std::vector<RawState::Pointer> pointers);
    int getAbsAxisInfo(int axis, struct AbsAxisInfo* axisInfo);
    int getEventAction(int action, int changeId, BitSet32 IdBits, RawState* state, std::vector<RawState::Pointer>* pointers);
    void dispatchTouches();
    void rotationTouches();
    void syncEvent();
    void sync();

    int mDisplayWidth;
    int mDisplayHeight;
    int mDisplayRotation;
    float mXScale;
    float mYScale;
    PointerAxes axes;
    MultiTouchMotionAccumulator accumultor;
    BitSet32 mPointerIdBits;
    int32_t mPointerTrackingIdMap[MAX_POINTER_ID + 1];

    RawState currentState;
    RawState lastState;
};

#endif //WIRELESSADBTEST_MULTITOUCHINPUTMAPPER_H
