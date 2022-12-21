//
// Created by makx on 2022/12/21.
//

#ifndef WIRELESSADBTEST_INPUTDEVICE_H
#define WIRELESSADBTEST_INPUTDEVICE_H

#include <array>
#include <bitset>
#include <android/log.h>
#include "InputMapper.h"
#include "MapperEventQueue.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif

#define LOG_TAG "inputdevice"

#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define KEYBOARD 0x00000001
#define ALPHAKEY 0x00000002
#define TOUCH    0x00000004
#define CURSOR   0x00000008
#define TOUCH_MT 0x00000010
#define DPAD     0x00000020
#define GAMEPAD  0x00000040
#define SWITCH   0x00000080
#define JOYSTICK 0x00000100
#define VIBRATOR 0x00000200
#define MIC      0x00000400
#define EXTERNAL_STYLUS  0x00000800
#define ROTARY_ENCODER   0x00001000
#define SENSOR   0x00002000
#define BATTERY  0x00004000
#define LIGHT    0x00008000
#define VIRTUAL  0x40000000
#define EXTERNAL 0x80000000

template <std::size_t BITS>
class BitArray {
    /* Array element type and vector of element type. */
    using Element = uint32_t;
    /* Number of bits in each BitArray element. */
    static constexpr size_t WIDTH = sizeof(Element) * CHAR_BIT;
    /* Number of elements to represent a bit array of the specified size of bits. */
    static constexpr size_t COUNT = (BITS + WIDTH - 1) / WIDTH;

public:
    /* BUFFER type declaration for BitArray */
    using Buffer = std::array<Element, COUNT>;
    /* To tell if a bit is set in array, it selects an element from the array, and test
     * if the relevant bit set.
     * Note the parameter "bit" is an index to the bit, 0 <= bit < BITS.
     */
    inline bool test(size_t bit) const {
        return (bit < BITS) ? mData[bit / WIDTH].test(bit % WIDTH) : false;
    }
    /* Returns total number of bytes needed for the array */
    inline size_t bytes() { return (BITS + CHAR_BIT - 1) / CHAR_BIT; }
    /* Returns true if array contains any non-zero bit from the range defined by start and end
     * bit index [startIndex, endIndex).
     */
    bool any(size_t startIndex, size_t endIndex) {
        if (startIndex >= endIndex || startIndex > BITS || endIndex > BITS + 1) {
            ALOGE("Invalid start/end index. start = %zu, end = %zu, total bits = %zu", startIndex,
                    endIndex, BITS);
            return false;
        }
        size_t se = startIndex / WIDTH; // Start of element
        size_t ee = endIndex / WIDTH;   // End of element
        size_t si = startIndex % WIDTH; // Start index in start element
        size_t ei = endIndex % WIDTH;   // End index in end element
        // Need to check first unaligned bitset for any non zero bit
        if (si > 0) {
            size_t nBits = se == ee ? ei - si : WIDTH - si;
            // Generate the mask of interested bit range
            Element mask = ((1 << nBits) - 1) << si;
            if (mData[se++].to_ulong() & mask) {
                return true;
            }
        }
        // Check whole bitset for any bit set
        for (; se < ee; se++) {
            if (mData[se].any()) {
                return true;
            }
        }
        // Need to check last unaligned bitset for any non zero bit
        if (ei > 0 && se <= ee) {
            // Generate the mask of interested bit range
            Element mask = (1 << ei) - 1;
            if (mData[se].to_ulong() & mask) {
                return true;
            }
        }
        return false;
    }
    /* Load bit array values from buffer */
    void loadFromBuffer(const Buffer& buffer) {
        for (size_t i = 0; i < COUNT; i++) {
            mData[i] = std::bitset<WIDTH>(buffer[i]);
        }
    }

private:
    std::array<std::bitset<WIDTH>, COUNT> mData;
};

struct InputDeviceIdentifier{
    inline InputDeviceIdentifier():bus(0),vendor(0),product(0), version(0){}

    std::string name;
    std::string location;
    std::string uniqueId;
    uint16_t bus;
    uint16_t vendor;
    uint16_t product;
    uint16_t version;
    std::string descriptor;
};

class InputDeviceContext;
class InputMapper;

#define MAPPER_COUNT 10

class InputDevice{
public:
    InputDevice(int fd, int32_t id, char* path, InputDeviceIdentifier& identifier);
    ~InputDevice();

    int readDeviceBitMask();
    uint32_t getAbsAxisUsage(int32_t axis, uint32_t classes);
    void addEventHubDevice(MapperEventQueue* queue);
    void removeEventHubDevice();
    void configure();
    void reset();
    void process(struct input_event* events, size_t count);
    void setDisplayInfo(int width, int height, int rotation);

private:
    template <std::size_t N>
    int readDeviceBitMask(unsigned long ioctlCode, BitArray<N>& bitArray);


public:
    int fd;
    int32_t id;
    std::string path;
    InputDeviceIdentifier identifier;
    
    BitArray<KEY_MAX> keyBitmask;
    BitArray<KEY_MAX> keyState;
    BitArray<ABS_MAX> absBitmask;
    BitArray<REL_MAX> relBitmask;
    BitArray<SW_MAX> swBitmask;
    BitArray<SW_MAX> swState;
    BitArray<LED_MAX> ledBitmask;
    BitArray<FF_MAX> ffBitmask;
    BitArray<INPUT_PROP_MAX> propBitmask;
    BitArray<MSC_MAX> mscBitmask;

    uint32_t classes;
    InputMapper* mappers[MAPPER_COUNT];
    int mapper_size;
};

class InputDeviceContext{
public:
    InputDeviceContext(InputDevice& device, MapperEventQueue* queue);
    ~InputDeviceContext();
    InputDevice getDevice(){return mDevice;}
    int getInputDeviceFd(){return mDevice.fd;}
    int getInputDeviceId(){return mDevice.id;}
    MapperEventQueue* getQueue(){return mQueue;}

private:
    InputDevice& mDevice;
    MapperEventQueue* mQueue;
};

#endif //WIRELESSADBTEST_INPUTDEVICE_H
