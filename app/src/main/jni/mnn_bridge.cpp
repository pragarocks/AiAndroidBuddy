#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "PocketPet_MNN"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifndef MNN_STUB_ONLY
#include "MNN/llm/llm.hpp"
using namespace MNN::Transformer;
#endif

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_pocketpet_core_ai_MnnLlmEngine_nativeInit(
        JNIEnv *env,
        jobject /* this */,
        jstring modelPath) {

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading MNN LLM from: %s", path);

#ifdef MNN_STUB_ONLY
    LOGI("MNN stub build — returning dummy handle");
    env->ReleaseStringUTFChars(modelPath, path);
    return 1L;
#else
    try {
        auto* llm = Llm::createLLM(std::string(path));
        if (!llm) {
            LOGE("Failed to create LLM from path: %s", path);
            env->ReleaseStringUTFChars(modelPath, path);
            return 0L;
        }
        llm->load();
        LOGI("MNN LLM loaded successfully");
        env->ReleaseStringUTFChars(modelPath, path);
        return reinterpret_cast<jlong>(llm);
    } catch (const std::exception& e) {
        LOGE("Exception loading LLM: %s", e.what());
        env->ReleaseStringUTFChars(modelPath, path);
        return 0L;
    }
#endif
}

JNIEXPORT jstring JNICALL
Java_com_pocketpet_core_ai_MnnLlmEngine_nativeGenerate(
        JNIEnv *env,
        jobject /* this */,
        jlong handle,
        jstring prompt,
        jint maxTokens) {

    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);

#ifdef MNN_STUB_ONLY
    LOGI("MNN stub generate — returning placeholder");
    env->ReleaseStringUTFChars(prompt, promptStr);
    return env->NewStringUTF("I'm thinking about that... (AI model not loaded yet)");
#else
    if (handle == 0) {
        env->ReleaseStringUTFChars(prompt, promptStr);
        return env->NewStringUTF("[error: model not loaded]");
    }

    try {
        auto* llm = reinterpret_cast<Llm*>(handle);
        std::string result = llm->response(std::string(promptStr), maxTokens);
        env->ReleaseStringUTFChars(prompt, promptStr);
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception& e) {
        LOGE("Exception during generate: %s", e.what());
        env->ReleaseStringUTFChars(prompt, promptStr);
        return env->NewStringUTF("[error: inference failed]");
    }
#endif
}

JNIEXPORT void JNICALL
Java_com_pocketpet_core_ai_MnnLlmEngine_nativeDestroy(
        JNIEnv *env,
        jobject /* this */,
        jlong handle) {

#ifdef MNN_STUB_ONLY
    LOGI("MNN stub destroy");
    return;
#else
    if (handle == 0) return;
    auto* llm = reinterpret_cast<Llm*>(handle);
    delete llm;
    LOGI("MNN LLM destroyed");
#endif
}

} // extern "C"
