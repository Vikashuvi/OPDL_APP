cmake_minimum_required(VERSION 3.22.1)
project(opdl_jni C)

add_library(opdl_jni SHARED opdl_jni.c)

find_library(log-lib log)

target_compile_options(opdl_jni PRIVATE
    -Wall
    -Wextra
    -Werror
)

target_link_libraries(opdl_jni
    ${log-lib}
)
