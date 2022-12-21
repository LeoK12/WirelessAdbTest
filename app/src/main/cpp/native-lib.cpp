#include <jni.h>
#include <string>
#include <cinttypes>
#include <openssl/curve25519.h>
#include <openssl/hkdf.h>
#include <openssl/evp.h>
#include "android/log.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif

#define LOG_TAG "PairingAuthCtx"

#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr spake2_role_t kClientRole = spake2_role_alice;
static constexpr spake2_role_t kServerRole = spake2_role_bob;

static const uint8_t kClientName[] = "adb pair client";
static const uint8_t kServerName[] = "adb pair server";

static constexpr size_t kHkdfKeyLength = 16;

struct PairingContextNative {
    SPAKE2_CTX *spake2_ctx;
    uint8_t key[SPAKE2_MAX_MSG_SIZE];
    size_t key_size;

    EVP_AEAD_CTX *aes_ctx;
    uint64_t dec_sequence;
    uint64_t enc_sequence;
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_leok12_wirelessadbtest_PairingAuthCtx_nativeConstructor(
        JNIEnv* env,
        jclass clazz,
        jboolean isClient,
        jbyteArray password) {
    spake2_role_t spake_role;
    const uint8_t* my_name;
    size_t my_len;
    const uint8_t* their_name;
    size_t their_len;

    if (isClient){
        spake_role = kClientRole;
        my_name = kClientName;
        my_len = sizeof(kClientName);
        their_name = kServerName;
        their_len = sizeof(kServerName);
    } else {
        spake_role = kServerRole;
        my_name = kServerName;
        my_len = sizeof(kServerName);
        their_name = kClientName;
        their_len = sizeof(kClientName);
    }

    auto spake2_ctx = SPAKE2_CTX_new(spake_role, my_name, my_len, their_name, their_len);
    if (spake2_ctx == nullptr){
        ALOGE("failed to create a SPAKE2 context.");
        return 0;
    }

    auto pswd = env->GetByteArrayElements(password, nullptr);
    auto pswd_size = env->GetArrayLength(password);

    size_t key_size = 0;
    uint8_t key[SPAKE2_MAX_MSG_SIZE];
    int status = SPAKE2_generate_msg(spake2_ctx, key, &key_size, SPAKE2_MAX_MSG_SIZE, (uint8_t *) pswd, pswd_size);
    if (status != 1 || key_size == 0){
        ALOGE("failed to generate SPAKE2 public key");
        env->ReleaseByteArrayElements(password, pswd, 0);
        SPAKE2_CTX_free(spake2_ctx);
        return 0;
    }

    env->ReleaseByteArrayElements(password, pswd, 0);

    auto ctx = (PairingContextNative*)malloc(sizeof(PairingContextNative));
    if (ctx == nullptr){
        ALOGE("failed to allocate PairingContextNative");
        SPAKE2_CTX_free(spake2_ctx);
        return 0;
    }

    memset(ctx, 0, sizeof(PairingContextNative));
    ctx->spake2_ctx = spake2_ctx;
    memcpy(ctx->key, key, SPAKE2_MAX_MSG_SIZE);
    ctx->key_size = key_size;
    return (jlong)ctx;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_leok12_wirelessadbtest_PairingAuthCtx_nativeMsg(
        JNIEnv* env,
        jobject clazz,
        jlong ptr) {
    auto ctx = (PairingContextNative *) ptr;
    jbyteArray our_msg = env->NewByteArray(ctx->key_size);
    env->SetByteArrayRegion(our_msg, 0, ctx->key_size, (jbyte *) ctx->key);
    return our_msg;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_leok12_wirelessadbtest_PairingAuthCtx_nativeInitCipher(
        JNIEnv* env,
        jobject clazz,
        jlong ptr,
        jbyteArray data){
    auto res = JNI_TRUE;

    auto ctx = (PairingContextNative *) ptr;
    auto spake2_ctx = ctx->spake2_ctx;
    auto their_msg_size = env->GetArrayLength(data);

    if (their_msg_size > SPAKE2_MAX_MSG_SIZE) {
        ALOGE("their_msg size [%d] greater then max size [%d].", their_msg_size, SPAKE2_MAX_MSG_SIZE);
        return JNI_FALSE;
    }

    auto their_msg = env->GetByteArrayElements(data, nullptr);

    size_t key_material_len = 0;
    uint8_t key_material[SPAKE2_MAX_KEY_SIZE];
    int status = SPAKE2_process_msg(spake2_ctx, key_material, &key_material_len,
                                    sizeof(key_material), (uint8_t *) their_msg, their_msg_size);

    env->ReleaseByteArrayElements(data, their_msg, 0);

    if (status != 1) {
        ALOGE("Unable to process their public key");
        return JNI_FALSE;
    }

    // --------
    uint8_t key[kHkdfKeyLength];
    uint8_t info[] = "adb pairing_auth aes-128-gcm key";

    status = HKDF(key, sizeof(key), EVP_sha256(), key_material, key_material_len, nullptr, 0, info,
                  sizeof(info) - 1);
    if (status != 1) {
        ALOGE("HKDF");
        return JNI_FALSE;
    }

    ctx->aes_ctx = EVP_AEAD_CTX_new(EVP_aead_aes_128_gcm(), key, sizeof(key), EVP_AEAD_DEFAULT_TAG_LENGTH);

    if (!ctx->aes_ctx) {
        ALOGE("EVP_AEAD_CTX_new");
        return JNI_FALSE;
    }

    return res;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_leok12_wirelessadbtest_PairingAuthCtx_nativeEncrypt(
                JNIEnv* env,
                jobject clazz,
                jlong ptr,
                jbyteArray data){
    auto ctx = (PairingContextNative *) ptr;
    auto aes_ctx = ctx->aes_ctx;

    auto in = env->GetByteArrayElements(data, nullptr);
    auto in_size = env->GetArrayLength(data);

    auto out_size = (size_t) in_size + EVP_AEAD_max_overhead(EVP_AEAD_CTX_aead(ctx->aes_ctx));
    uint8_t out[out_size];

    auto nonce_size = EVP_AEAD_nonce_length(EVP_AEAD_CTX_aead(aes_ctx));
    uint8_t nonce[nonce_size];
    memset(nonce, 0, nonce_size);
    memcpy(nonce, &ctx->enc_sequence, sizeof(ctx->enc_sequence));

    size_t written_sz;
    int status = EVP_AEAD_CTX_seal(aes_ctx, out, &written_sz, out_size, nonce, nonce_size, (uint8_t *) in, in_size, nullptr, 0);

    env->ReleaseByteArrayElements(data, in, 0);

    if (!status) {
        ALOGE("Failed to encrypt (in_len=%d, out_len=%" PRIuPTR", out_len_needed=%d)", in_size, out_size, in_size);
        return nullptr;
    }
    ++ctx->enc_sequence;

    jbyteArray jOut = env->NewByteArray(written_sz);
    env->SetByteArrayRegion(jOut, 0, written_sz, (jbyte *) out);
    return jOut;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_leok12_wirelessadbtest_PairingAuthCtx_nativeDecrypt(
        JNIEnv* env,
        jobject clazz,
        jlong ptr,
        jbyteArray data){
    auto ctx = (PairingContextNative *) ptr;
    auto aes_ctx = ctx->aes_ctx;

    auto in = env->GetByteArrayElements(data, nullptr);
    auto in_size = env->GetArrayLength(data);

    auto out_size = (size_t) in_size;
    uint8_t out[out_size];

    auto nonce_size = EVP_AEAD_nonce_length(EVP_AEAD_CTX_aead(aes_ctx));
    uint8_t nonce[nonce_size];
    memset(nonce, 0, nonce_size);
    memcpy(nonce, &ctx->dec_sequence, sizeof(ctx->dec_sequence));

    size_t written_sz;
    int status = EVP_AEAD_CTX_open(aes_ctx, out, &written_sz, out_size, nonce, nonce_size, (uint8_t *) in, in_size, nullptr, 0);

    env->ReleaseByteArrayElements(data, in, 0);

    if (!status) {
        ALOGE("Failed to decrypt (in_len=%d, out_len=%" PRIuPTR", out_len_needed=%d)", in_size, out_size, in_size);
        return nullptr;
    }
    ++ctx->dec_sequence;

    jbyteArray jOut = env->NewByteArray(written_sz);
    env->SetByteArrayRegion(jOut, 0, written_sz, (jbyte *) out);
    return jOut;
}

extern "C" JNIEXPORT void JNICALL
Java_com_leok12_wirelessadbtest_PairingAuthCtx_nativeDestroy(
        JNIEnv* env,
        jobject clazz,
        jlong ptr){
    auto ctx = (PairingContextNative *) ptr;
    SPAKE2_CTX_free(ctx->spake2_ctx);
    if (ctx->aes_ctx) EVP_AEAD_CTX_free(ctx->aes_ctx);
    free(ctx);
}