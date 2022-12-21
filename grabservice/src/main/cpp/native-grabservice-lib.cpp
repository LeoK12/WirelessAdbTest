#include <jni.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <errno.h>
#include <android/log.h>
#include <pthread.h>
#include <vector>
#include "InputReader.h"
#include "MapperOutputEvent.h"
#include "MapperEventQueue.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif

#define LOG_TAG "grabservice"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static const int MAX_PACKAGE_NAME_SIZE = 256;

static struct {
    jmethodID interceptMotionData;
    jmethodID interceptKeyData;
} gGrabServiceClassInfo;

static struct {
    jclass clazz;
    jmethodID ctor;
} gGrabMotionDataClassInfo;

static struct {
    jclass clazz;
    jmethodID ctor;
} gGrabPointerClassInfo;

static struct {
    jclass clazz;
    jmethodID ctor;
} gGrabKeyDataClassInfo;

std::unique_ptr<InputReader> mInputReader;
jobject mServiceObj;
JavaVM* mJavaVM;
MapperEventQueue* mOutputQueue;
int mDisplayWidth = 0;
int mDisplayHeight = 0;
int mDisplayRotation = 0;

JNIEnv* getJNIEnv(){
    JNIEnv* env;
    mJavaVM->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_4);
    mJavaVM->AttachCurrentThread(&env, nullptr);
    return env;
}

void putJNIEnv(){
    mJavaVM->DetachCurrentThread();
}

void processOutput(OutputEvent* event){
    if (event == nullptr){
        return;
    }

    JNIEnv* env = getJNIEnv();
    if (event->type == OUTPUT_KEY_EVENT){
        OutputKeyEvent* keyEvent = (OutputKeyEvent*)event;
        int deviceId = event->deviceId;
        int keyCode = keyEvent->keyCode;
        int action = keyEvent->action;
        jobject data = env->NewObject(gGrabKeyDataClassInfo.clazz, gGrabKeyDataClassInfo.ctor,
                                      deviceId, keyCode, action);
        if (data == NULL) {
            putJNIEnv();
            return;
        }

        env->CallVoidMethod(mServiceObj, gGrabServiceClassInfo.interceptKeyData, data);
        if (env->ExceptionCheck()) {
            ALOGE("An exception was thrown by callback interceptKeyData\n");
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        env->DeleteLocalRef(data);
    } else {
        OutputMotionEvent* motionEvent = (OutputMotionEvent*)event;
        int deviceId = event->deviceId;
        int action = motionEvent->action;
        size_t count = motionEvent->pointerCount;
        OutputPointer *pointers = (OutputPointer *) &motionEvent->pointers;
        jobjectArray pointersObjArray = env->NewObjectArray(count, gGrabPointerClassInfo.clazz,
                                                            nullptr);
        if (pointersObjArray == NULL) {
            putJNIEnv();
            return;
        }

        for (size_t i = 0; i < count; i++) {
            jobject pointerObj = env->NewObject(gGrabPointerClassInfo.clazz,
                                                gGrabPointerClassInfo.ctor,
                                                pointers[i].id, pointers[i].x, pointers[i].y,
                                                pointers[i].touchMajor, pointers[i].touchMinor,
                                                pointers[i].widthMajor, pointers[i].widthMinor,
                                                pointers[i].orientation, pointers[i].pressure,
                                                pointers[i].distance, pointers[i].tooltype);
            if (pointerObj == NULL) {
                putJNIEnv();
                return;
            }


            ALOGE("[%s:%d] id = %d, x = %d, y = %d, touchMajor = %d, touchMinor = %d, widthMajor = %d, \
                widthMinor = %d, orientation = %d, pressure = %d, distance = %d, tooltype = %d\n",
                  __func__, __LINE__,
                  pointers[i].id, pointers[i].x, pointers[i].y, pointers[i].touchMajor,
                  pointers[i].touchMinor,
                  pointers[i].widthMajor, pointers[i].widthMinor, pointers[i].orientation,
                  pointers[i].pressure,
                  pointers[i].distance, pointers[i].tooltype);
            env->SetObjectArrayElement(pointersObjArray, i, pointerObj);
            env->DeleteLocalRef(pointerObj);
        }

        jobject data = env->NewObject(gGrabMotionDataClassInfo.clazz, gGrabMotionDataClassInfo.ctor,
                                      deviceId, action, pointersObjArray);
        if (data == NULL) {
            env->DeleteLocalRef(pointersObjArray);
            putJNIEnv();
            return;
        }

        env->CallVoidMethod(mServiceObj, gGrabServiceClassInfo.interceptMotionData, data);
        if (env->ExceptionCheck()) {
            ALOGE("An exception was thrown by callback interceptMotionData\n");
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        env->DeleteLocalRef(pointersObjArray);
        env->DeleteLocalRef(data);
    }
    putJNIEnv();
}

int startInputReader(){
    mOutputQueue = new MapperEventQueue();
    mOutputQueue->setProcessCallback(processOutput);
    mOutputQueue->init();
    mInputReader = std::make_unique<InputReader>(std::make_unique<EventHub>(), mOutputQueue);
    mInputReader->updateDisplayInfo(mDisplayWidth, mDisplayHeight, mDisplayRotation);
    mInputReader->start();
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_leok12_grabservice_GrabService_nativeInit(
        JNIEnv* env,
        jclass /* clazz */,
        jobject serviceObj) {
    mServiceObj = env->NewGlobalRef(serviceObj);
    env->GetJavaVM(&mJavaVM);
    startInputReader();
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_leok12_grabservice_GrabService_nativeSetDisplayInfo(
        JNIEnv* env,
        jclass /* clazz */,
        jint width,
        jint height,
        jint rotation) {
    mDisplayWidth = width;
    mDisplayHeight = height;
    mDisplayRotation = rotation;
#if 1
    ALOGE("[%s:%d]width = %d, height = %d, rotation = %d", __func__, __LINE__, width, height, rotation);
    if (mInputReader != nullptr){
        mInputReader->updateDisplayInfo(width, height, rotation);
    }
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_com_leok12_grabservice_GrabService_nativeUpdateTopPackageName(
        JNIEnv* env,
        jclass /* clazz */,
        jstring packageName){
    if (mInputReader == nullptr){
        ALOGE("input reader is not init");
        return 0;
    }

    int fd = open(mInputReader->getAppSwitchPath(), O_WRONLY|O_CLOEXEC);
    if (fd < 0){
        ALOGE("could not open %s, %s\n", mInputReader->getAppSwitchPath(), strerror(errno));
        return -errno;
    }

    char buffer[MAX_PACKAGE_NAME_SIZE];
    memset(buffer, 0, sizeof(buffer));
    const char* buf = env->GetStringUTFChars(packageName, NULL);
    sprintf(buffer, buf, strlen(buf));
    env->ReleaseStringUTFChars(packageName, buf);
    int err = write(fd, buffer, sizeof(buffer));
    if (err != sizeof(buffer)){
        close(fd);
        return -errno;
    }

    close(fd);
    return 0;
}

int register_com_leok12_grabservice_GrabSerervice(JNIEnv* env) {
    jclass clazz = env->FindClass("com/leok12/grabservice/GrabService");
    if (clazz == NULL) {
        ALOGE("Unable to find class 'GrabService'\n");
        return JNI_ERR;
    }

    gGrabServiceClassInfo.interceptMotionData =
            env->GetMethodID(clazz, "interceptData", "(Lcom/leok12/grabservice/GrabMotionData;)V");
    if (gGrabServiceClassInfo.interceptMotionData == NULL) {
        ALOGE("Unable to obtain  methods : %s\n", "interceptMotionData");
        return JNI_ERR;
    }

    gGrabServiceClassInfo.interceptKeyData =
            env->GetMethodID(clazz, "interceptData", "(Lcom/leok12/grabservice/GrabKeyData;)V");
    if (gGrabServiceClassInfo.interceptKeyData == NULL) {
        ALOGE("Unable to obtain  methods : %s\n", "interceptKeyData");
        return JNI_ERR;
    }

    return JNI_OK;
}

int register_com_leok12_grabservice_GrabMotionData(JNIEnv* env) {
    gGrabMotionDataClassInfo.clazz = env->FindClass("com/leok12/grabservice/GrabMotionData");
    if (gGrabMotionDataClassInfo.clazz == NULL){
        ALOGE("Unable to find class 'GrabMotionData'\n");
        return JNI_ERR;
    }

    gGrabMotionDataClassInfo.clazz = (jclass)env->NewGlobalRef(gGrabMotionDataClassInfo.clazz);
    if (gGrabMotionDataClassInfo.clazz == NULL){
        ALOGE("Unable to create global reference. 'GrabMotionData'\n");
        return JNI_ERR;
    }

    gGrabMotionDataClassInfo.ctor = env->GetMethodID(gGrabMotionDataClassInfo.clazz, "<init>", "(II[Lcom/leok12/grabservice/GrabMotionData$Pointer;)V");
    if (gGrabMotionDataClassInfo.ctor == NULL){
        ALOGE("Unable to obtain methods data: %s\n", "gGrabMotionDataClassInfo.ctor");
        return JNI_ERR;
    }

    gGrabPointerClassInfo.clazz = env->FindClass("com/leok12/grabservice/GrabMotionData$Pointer");
    if (gGrabPointerClassInfo.clazz == NULL){
        ALOGE("Unable to find class 'Pointer'\n");
        return JNI_ERR;
    }

    gGrabPointerClassInfo.clazz = (jclass)env->NewGlobalRef(gGrabPointerClassInfo.clazz);
    if (gGrabPointerClassInfo.clazz == NULL){
        ALOGE("Unable to create global reference. 'Pointer'\n");
        return JNI_ERR;
    }

    gGrabPointerClassInfo.ctor = env->GetMethodID(gGrabPointerClassInfo.clazz, "<init>", "(IIIIIIIIIII)V");
    if (gGrabPointerClassInfo.ctor == NULL){
        ALOGE("Unable to obtain methods Pointer: %s\n", "gGrabPointerClassInfo.ctor");
        return JNI_ERR;
    }

    return JNI_OK;
}

int register_com_leok12_grabservice_GrabKeyData(JNIEnv* env) {
    gGrabKeyDataClassInfo.clazz = env->FindClass("com/leok12/grabservice/GrabKeyData");
    if (gGrabKeyDataClassInfo.clazz == NULL){
        ALOGE("Unable to find class 'GrabKeyData'\n");
        return JNI_ERR;
    }

    gGrabKeyDataClassInfo.clazz = (jclass)env->NewGlobalRef(gGrabKeyDataClassInfo.clazz);
    if (gGrabKeyDataClassInfo.clazz == NULL){
        ALOGE("Unable to create global reference. 'GrabKeyData'\n");
        return JNI_ERR;
    }

    gGrabKeyDataClassInfo.ctor = env->GetMethodID(gGrabKeyDataClassInfo.clazz, "<init>", "(III)V");
    if (gGrabKeyDataClassInfo.ctor == NULL){
        ALOGE("Unable to obtain methods data: %s\n", "gBsGrabKeyDataClassInfo.ctor");
        return JNI_ERR;
    }

    return JNI_OK;
}

extern "C" jint JNI_OnLoad(JavaVM* vm, void* /* reserved */){
    JNIEnv* env = NULL;
    jint result = -1;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        ALOGE("GetEnv failed!\n");
        return result;
    }

    if (env == NULL){
       ALOGE("Could not retrieve the env!\n");
       return result;
    }

    result = register_com_leok12_grabservice_GrabSerervice(env);
    if (result){
        ALOGE("failed to register_com_leok12_grabservice_GrabSerervice\n");
        return result;
    }

    result = register_com_leok12_grabservice_GrabMotionData(env);
    if (result){
        ALOGE("failed to register_com_leok12_grabservice_GrabMotionData\n");
        return result;
    }

    result = register_com_leok12_grabservice_GrabKeyData(env);
    if (result){
        ALOGE("failed to register_com_leok12_grabservice_GrabKeyData\n");
        return result;
    }

    return JNI_VERSION_1_4;
}
