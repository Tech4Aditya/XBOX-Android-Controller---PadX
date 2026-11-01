cmake_minimum_required(VERSION 3.15)
project(padx_server CXX)
set(CMAKE_CXX_STANDARD 17)

# git clone https://github.com/nefarius/ViGEmClient  (next to this file)
add_subdirectory(ViGEmClient)

add_executable(padx_server server.cpp)
target_include_directories(padx_server PRIVATE ViGEmClient/include)
target_link_libraries(padx_server PRIVATE ViGEmClient ws2_32 setupapi)
