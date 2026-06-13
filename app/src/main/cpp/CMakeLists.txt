cmake_minimum_required(VERSION 3.22.1)

# Set the project name
project("geosnap_native")

# Compile C++ with standard 17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Define the shared library target
add_library(geosnap_native SHARED
        native_processing.cpp
)

# Locate Android system libraries
find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

# Link dependencies to our library
target_link_libraries(geosnap_native
        ${log-lib}
        ${android-lib}
        ${jnigraphics-lib}
)
