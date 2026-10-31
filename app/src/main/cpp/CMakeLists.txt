cmake_minimum_required(VERSION 3.22.1)
project(obeli_native)

include(FetchContent)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
FetchContent_Declare(llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG 79bce0c8812ddfe4716922d061b67edc2ed517e9
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(llama_cpp)
add_library(obeli SHARED obeli_jni.cpp)
target_include_directories(obeli PRIVATE ${llama_cpp_SOURCE_DIR}/include)
target_link_libraries(obeli PRIVATE llama android log)
