cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(whisper STATIC
    third_party/whisper.cpp/whisper.cpp
)

target_include_directories(whisper PUBLIC
    third_party/whisper.cpp
)

add_library(whisper_jni SHARED
    whisper_jni.cpp
)

target_link_libraries(whisper_jni
    whisper
    log
)
