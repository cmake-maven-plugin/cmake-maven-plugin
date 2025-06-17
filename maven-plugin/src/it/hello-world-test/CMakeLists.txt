cmake_minimum_required(VERSION 4.0.3)
project(HelloTest)
add_executable(hello-test main.c)

# Turn on testing
enable_testing()
include(CTest)

# Tell ctest where to find its tests
add_subdirectory(tests)

# Install compiled executable into the specified subdirectory
install(PROGRAMS ${CMAKE_CURRENT_BINARY_DIR}/$<CONFIG>/hello-test${CMAKE_EXECUTABLE_SUFFIX} DESTINATION install-dir)
