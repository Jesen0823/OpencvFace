#include <jni.h>
#include <string>
#include <opencv2/opencv.hpp>
#include <opencv2/objdetect.hpp>
#include "opencv2/opencv.hpp"
#include "opencv2/core/cvstd_wrapper.hpp"

using namespace cv;
extern "C"
JNIEXPORT void JNICALL
Java_com_jesen_opencvface_OpencvHelp_init(JNIEnv *env, jobject thiz, jstring path_) {
    const char *path = env->GetStringUTFChars(path_,0);

    makePtr<cv::CascadeClassifier>(path);

    env->ReleaseStringUTFChars(path_,path);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_jesen_opencvface_OpencvHelp_setSurface(JNIEnv *env, jobject thiz, jobject surface) {
    // TODO: implement setSurface()
}
extern "C"
JNIEXPORT void JNICALL
Java_com_jesen_opencvface_OpencvHelp_postData(JNIEnv *env, jobject thiz, jbyteArray data,
                                              jint width, jint height, jint camera_id) {
    // TODO: implement postData()
}