cmake_minimum_required(VERSION 3.18)
project(genie_jni)

add_library(genie_jni SHARED genie_jni.cpp)

target_include_directories(genie_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

find_library(log-lib log)

add_library(libGenie SHARED IMPORTED)
set_target_properties(libGenie PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libGenie.so
)

add_library(libappbuilder SHARED IMPORTED)
set_target_properties(libappbuilder PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libappbuilder.so
)

target_link_libraries(genie_jni libGenie libappbuilder ${log-lib})
