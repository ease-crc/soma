##############################################################################
# CMake
##############################################################################

cmake_minimum_required(VERSION 2.8.3)
project(soma)

find_package(catkin REQUIRED COMPONENTS dul)

catkin_package(CATKIN_DEPENDS dul)

##############################################################################
# Catkin
##############################################################################

install(DIRECTORY owl/
  DESTINATION ${CATKIN_PACKAGE_SHARE_DESTINATION}/owl)

