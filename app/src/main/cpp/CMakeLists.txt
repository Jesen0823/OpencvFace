cmake_minimum_required(VERSION 3.10.2)

project("opencvface")

add_library( 
        opencvface
        
        SHARED

        native-lib.cpp)

# 导入头文件
include_directories(${CMAKE_SOURCE_DIR}/include)

# 导入动态库
set(my_lib_path ${CMAKE_SOURCE_DIR}/../../../libs/${CMAKE_ANDROID_ARCH_ABI})
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -L${my_lib_path}")

find_library( 
        log-lib
        log)

target_link_libraries( 
        opencvface
        opencv_java4
        ${log-lib})