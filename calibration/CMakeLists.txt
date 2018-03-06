cmake_minimum_required(VERSION 2.8)
project( feature_tracking )
find_package( OpenCV )
include_directories( Eigen )
include_directories( ${OpenCV_INCLUDE_DIRS} )

add_executable( feature_tracking feature_tracking.cpp )
target_link_libraries( feature_tracking ${OpenCV_LIBS} )
