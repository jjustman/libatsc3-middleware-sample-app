#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_nextgenbroadcast_mobile_middleware_Auth0_username(JNIEnv *env, jobject thiz) {
    std::string str = "";
    return env->NewStringUTF(str.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_nextgenbroadcast_mobile_middleware_Auth0_password(JNIEnv *env, jobject thiz) {
    std::string str = "";
    return env->NewStringUTF(str.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_nextgenbroadcast_mobile_middleware_Auth0_clientId(JNIEnv *env, jobject thiz) {
    std::string str = "";
    return env->NewStringUTF(str.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_nextgenbroadcast_mobile_middleware_Auth0_clientSecret(JNIEnv *env, jobject thiz) {
    std::string str = "";
    return env->NewStringUTF(str.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_nextgenbroadcast_mobile_middleware_Auth0_clientKey(JNIEnv *env, jobject thiz) {
    std::string str = "bWlkZGxld2FyZTozMzY3ZWZlOWIzMjQ0NDdhYTYwZGEyM2U5NjBhYmQ2OA==";
    return env->NewStringUTF(str.c_str());
}