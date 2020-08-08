##############################################################################
# CMake
##############################################################################

cmake_minimum_required(VERSION 2.8.3)
project(soma)

find_package(catkin REQUIRED COMPONENTS dul)

catkin_package(CATKIN_DEPENDS dul)

# NOTE: this step requires owlready2 Python library.
#       install by calling `pip install owlready2`.
add_custom_command(
	OUTPUT "build/SOMA.owl"
    COMMAND swipl -f prolog/uglify.pl -t uglify
    COMMAND python3 ./scripts/uglify_ease_as_owl.py
    WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}"
    COMMENT "Creating build/SOMA.owl"
)
add_custom_target(soma ALL DEPENDS "build/SOMA.owl")

##############################################################################
# Catkin
##############################################################################

install(DIRECTORY owl/
  DESTINATION ${CATKIN_PACKAGE_SHARE_DESTINATION}/owl)

