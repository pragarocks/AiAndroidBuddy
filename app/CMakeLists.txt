cmake_minimum_required(VERSION 3.22)
project(pocketpet_native)

set(CMAKE_CXX_STANDARD 17)

# MNN prebuilt AAR ships libMNN.so — link against it
find_library(MNN_LIB MNN)

# Fall back gracefully if MNN AAR not present (for CI/stub builds)
if(NOT MNN_LIB)
    message(WARNING "MNN library not found — building stub-only JNI bridge")
    set(STUB_ONLY TRUE)
endif()

add_library(mnn_bridge SHARED
    src/main/jni/mnn_bridge.cpp
)

target_include_directories(mnn_bridge PRIVATE
    src/main/jni
)

if(STUB_ONLY)
    target_compile_definitions(mnn_bridge PRIVATE MNN_STUB_ONLY=1)
    target_link_libraries(mnn_bridge android log)
else()
    target_link_libraries(mnn_bridge
        ${MNN_LIB}
        android
        log
    )
endif()
