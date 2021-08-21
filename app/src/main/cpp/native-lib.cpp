#include <jni.h>
#include <string>
#include <opencv2/objdetect.hpp>
#include <android/native_window_jni.h>
#include "opencv2/opencv.hpp"
#include "opencv2/core/cvstd_wrapper.hpp"

using namespace cv;
ANativeWindow *window = 0;
DetectionBasedTracker *tracker = 0;

class CascadeDetectorAdapter : public DetectionBasedTracker::IDetector {
public:
    CascadeDetectorAdapter(cv::Ptr<cv::CascadeClassifier> detector) :
            IDetector(), Detector(detector) {

    }

    void detect(const cv::Mat &Image, std::vector<cv::Rect> &objects) {
        Detector->detectMultiScale(Image, objects, scaleFactor, minNeighbours, 0,
                                   minObjSize,maxObjSize);
    }

    virtual  ~CascadeDetectorAdapter() {}

private:
    CascadeDetectorAdapter();

    cv::Ptr<cv::CascadeClassifier> Detector;
};

int i = 0;
extern "C"
JNIEXPORT void JNICALL
Java_com_jesen_opencvface_OpencvHelp_init(JNIEnv *env, jobject thiz, jstring path_) {
    const char *path = env->GetStringUTFChars(path_, 0);

    // 智能指针
    Ptr<CascadeClassifier> classifier = makePtr<CascadeClassifier>(path);
    // 创建检测器
    Ptr<CascadeDetectorAdapter> mainDetector = makePtr<CascadeDetectorAdapter>(classifier);
    // 跟踪器
    Ptr<CascadeClassifier> classifier1 = makePtr<CascadeClassifier>(path);
    // 创建检测器
    Ptr<CascadeDetectorAdapter> trackingDetector = makePtr<CascadeDetectorAdapter>(classifier1);

    DetectionBasedTracker::Parameters DetectorParams;
    // tracker含有两个对象：检测器 and 跟踪器
    tracker = new DetectionBasedTracker(mainDetector, trackingDetector, DetectorParams);
    tracker->run();

    env->ReleaseStringUTFChars(path_, path);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_jesen_opencvface_OpencvHelp_setSurface(JNIEnv *env, jobject thiz, jobject surface) {
    if (window) {
        ANativeWindow_release(window);
        window = 0;
    }
    window = ANativeWindow_fromSurface(env, surface);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_jesen_opencvface_OpencvHelp_postData(JNIEnv *env, jobject thiz, jbyteArray data_,
                                              jint width, jint height, jint camera_id) {
    // NV21-----> Bitmap-----> Mat
    jbyte *data = env->GetByteArrayElements(data_, NULL);
    Mat src(height + height / 2, width, CV_8UC1, data);
    cvtColor(src, src, COLOR_YUV2BGRA_NV21);
    if (camera_id == 1) {
        // 前置摄像头
        rotate(src, src, ROTATE_90_COUNTERCLOCKWISE);
        // 1 水平翻转，镜像 0 处置翻转
        flip(src, src, 1);
    } else {
        // 后置摄像头
        // 顺时针旋转90度
        rotate(src, src, ROTATE_90_CLOCKWISE);
    }
    Mat gray;
    cvtColor(src, gray, COLOR_RGBA2GRAY);
    // 对比度
    equalizeHist(gray, gray);
    // 检测人脸
    std::vector<Rect> faces;
    tracker->process(gray);
    tracker->getObjects(faces);
    for (Rect face:faces) {
        rectangle(src, face, Scalar(255, 0, 255));
    }

    if (window) {
        ANativeWindow_setBuffersGeometry(window, src.cols, src.rows, WINDOW_FORMAT_RGBA_8888);
        ANativeWindow_Buffer window_buffer;
        do {
            //lock失败 直接break出去
            if (ANativeWindow_lock(window, &window_buffer, 0)) {
                ANativeWindow_release(window);
                window = 0;
                break;
            }
            //src.data ： rgba的数据
            //把src.data 拷贝到 buffer.bits 里去
            // 一行一行的拷贝
            //填充rgb数据给dst_data
            uint8_t *dst_data = static_cast<uint8_t *>(window_buffer.bits);
            //stride : 一行多少个数据 （RGBA） * 4
            int dst_linesize = window_buffer.stride * 4;

            //一行一行拷贝
            for (int i = 0; i < window_buffer.height; ++i) {
                memcpy(dst_data + i * dst_linesize, src.data + i * src.cols * 4, dst_linesize);
            }
            //提交刷新
            ANativeWindow_unlockAndPost(window);
        } while (0);
    }
    src.release();
    gray.release();
    env->ReleaseByteArrayElements(data_, data, 0);
}










