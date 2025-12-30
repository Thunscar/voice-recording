
# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("recording")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/common_audio
)

add_library(${CMAKE_PROJECT_NAME} SHARED
    # List C/C++ source files with relative paths to this CMakeLists.txt.
        common_audio/ring_buffer.c
        common_audio/ring_buffer.h
        common_audio/signal_processing/auto_corr_to_refl_coef.c
        common_audio/signal_processing/auto_correlation.c
        common_audio/signal_processing/complex_fft_tables.h
        common_audio/signal_processing/copy_set_operations.c
        common_audio/signal_processing/cross_correlation.c
        common_audio/signal_processing/division_operations.c
        common_audio/signal_processing/downsample_fast.c
        common_audio/signal_processing/energy.c
        common_audio/signal_processing/filter_ar.c
        common_audio/signal_processing/filter_ma_fast_q12.c
        common_audio/signal_processing/get_hanning_window.c
        common_audio/signal_processing/get_scaling_square.c
        common_audio/signal_processing/include/real_fft.h
        common_audio/signal_processing/include/signal_processing_library.h
        common_audio/signal_processing/include/spl_inl.h
        common_audio/signal_processing/include/spl_inl_armv7.h
        common_audio/signal_processing/levinson_durbin.c
        common_audio/signal_processing/lpc_to_refl_coef.c
        common_audio/signal_processing/min_max_operations.c
        common_audio/signal_processing/randomization_functions.c
        common_audio/signal_processing/real_fft.c
        common_audio/signal_processing/refl_coef_to_lpc.c
        common_audio/signal_processing/resample.c
        common_audio/signal_processing/resample_48khz.c
        common_audio/signal_processing/resample_by_2.c
        common_audio/signal_processing/resample_by_2_internal.c
        common_audio/signal_processing/resample_by_2_internal.h
        common_audio/signal_processing/resample_fractional.c
        common_audio/signal_processing/spl_init.c
        common_audio/signal_processing/spl_inl.c
        common_audio/signal_processing/spl_sqrt.c
        common_audio/signal_processing/splitting_filter.c
        common_audio/signal_processing/sqrt_of_one_minus_x_squared.c
        common_audio/signal_processing/vector_operations.c
        common_audio/signal_processing/vector_scaling_operations.c
        common_audio/vad/include/webrtc_vad.h
        common_audio/vad/vad_core.c
        common_audio/vad/vad_core.h
        common_audio/vad/vad_filterbank.c
        common_audio/vad/vad_filterbank.h
        common_audio/vad/vad_gmm.c
        common_audio/vad/vad_gmm.h
        common_audio/vad/vad_sp.c
        common_audio/vad/vad_sp.h
        common_audio/vad/webrtc_vad.c

        common_audio/signal_processing/complex_fft.c
        common_audio/signal_processing/complex_bit_reverse.c
        common_audio/signal_processing/filter_ar_fast_q12.c
    recording.cpp)

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
    # List libraries link to the target library
    android
    log)
