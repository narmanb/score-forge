cmake_minimum_required(VERSION 3.22.1)

project(scoreforge_native LANGUAGES CXX)

add_library(
    scoreforge_native
    SHARED
    native_fluidsynth.cpp
)

find_package(fluidsynth-android REQUIRED CONFIG)
find_library(log-lib log)

target_compile_features(scoreforge_native PRIVATE cxx_std_17)

target_link_libraries(
    scoreforge_native
    fluidsynth-android::fluidsynth
    ${log-lib}
)
