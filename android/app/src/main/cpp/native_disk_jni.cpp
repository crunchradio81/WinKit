#include "disk_image.h"
#ifdef __ANDROID__
#include <jni.h>
#include <stdexcept>
extern "C" JNIEXPORT jlong JNICALL
Java_org_winkit_android_NativeDisk_probe(JNIEnv *env, jclass, jstring path) {
    if (!path) return -1;
    const char *cpath = env->GetStringUTFChars(path, nullptr);
    if (!cpath) return -1;
    try {
        winkit::DiskImage img;
        img.openExclusive(cpath, false);
        const auto bytes = img.bytes();
        env->ReleaseStringUTFChars(path, cpath);
        return static_cast<jlong>(bytes);
    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(path, cpath);
        jclass cls = env->FindClass("java/io/IOException");
        if (cls) env->ThrowNew(cls, e.what());
        return -1;
    }
}
#endif
