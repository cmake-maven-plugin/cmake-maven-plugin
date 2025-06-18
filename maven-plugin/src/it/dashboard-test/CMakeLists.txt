# ARMv6 only supports version 3.18.4
cmake_minimum_required(VERSION 3.18.4)
project(DashboardTest)
add_executable(dashboard-test main.c)

# Turn on testing
enable_testing()
include(CTest)

# Tell ctest where to find its tests
add_subdirectory(tests)