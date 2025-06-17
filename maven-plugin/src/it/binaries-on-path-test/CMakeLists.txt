cmake_minimum_required(VERSION 4.0.3)
project(BinariesOnPathTest)
add_executable(binaries-on-path-test main.c)

# Turn on testing
enable_testing()
include(CTest)

# Tell ctest where to find its tests
add_subdirectory(tests)