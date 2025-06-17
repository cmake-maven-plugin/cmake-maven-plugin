cmake_minimum_required(VERSION 4.0.3)
project(ExplicitGeneratorTest)
add_executable(explicit-generator main.c)

# Turn on testing
enable_testing()
include(CTest)

# Tell ctest where to find its tests
add_subdirectory(tests)