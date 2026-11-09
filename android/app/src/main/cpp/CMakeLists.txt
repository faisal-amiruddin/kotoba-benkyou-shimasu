cmake_minimum_required(VERSION 3.22.1)
project(kotoba_voicevox)
add_library(voicevox_core SHARED IMPORTED)
# Official CORE binaries have no SONAME. Link by library name so DT_NEEDED does
# not embed the CI machine's absolute path, which cannot exist on the phone.
set_target_properties(voicevox_core PROPERTIES IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libvoicevox_core.so" IMPORTED_NO_SONAME TRUE)
add_library(kotoba_voicevox SHARED voicevox_jni.cpp)
target_include_directories(kotoba_voicevox PRIVATE vendor)
target_compile_definitions(kotoba_voicevox PRIVATE VOICEVOX_LOAD_ONNXRUNTIME)
target_link_options(kotoba_voicevox PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(kotoba_voicevox voicevox_core log)
