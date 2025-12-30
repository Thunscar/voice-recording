#include "jni.h"
#include "stdint.h"
#include "android/log.h"
#include "common_audio/vad/include/webrtc_vad.h"

#define TAG "NativeVAD"
#define LOG(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)


#ifdef __cplusplus
extern "C" {
#endif


static VadInst *vad = NULL;

//初始化函数
JNIEXPORT void JNICALL
Java_com_stitchcodes_recording_vad_webrtc_VadNative_init(JNIEnv *env, jobject thiz, jint mode) {
    LOG("vad native init start..., mode:[%d]", mode);
    vad = WebRtcVad_Create();
    WebRtcVad_Init(vad);
    WebRtcVad_set_mode(vad, mode); // 0~3
    LOG("vad native init success, vad mode:[%d]", mode);
}

JNIEXPORT jboolean JNICALL
Java_com_stitchcodes_recording_vad_webrtc_VadNative_hasVoice(JNIEnv *env, jobject thiz, jint fs, jshortArray frameArray) {
    jshort *data = env->GetShortArrayElements(frameArray, nullptr);
    jsize length = env->GetArrayLength(frameArray);
    int isVoice = WebRtcVad_Process(vad, fs, data, length);
    return isVoice == 1;
}

#ifdef __cplusplus
}
#endif
