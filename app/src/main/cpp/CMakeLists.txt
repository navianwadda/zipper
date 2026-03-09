cmake_minimum_required(VERSION 3.22.1)

project("livetvpro")

add_library(
        native-lib
        SHARED
        native-lib.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        native-lib
        ${log-lib}
        android
        dl
)

target_compile_options(native-lib PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
