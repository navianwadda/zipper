# app/src/main/cpp/CMakeLists.txt
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

# Note: libffmpegjni.so is provided by the Jellyfin Media3 FFmpeg AAR
# (org.jellyfin.media3:media3-ffmpeg-decoder). Gradle packages it into the APK
# automatically — no manual linking needed here.
target_link_libraries(
        native-lib
        ${log-lib}
        android
)
