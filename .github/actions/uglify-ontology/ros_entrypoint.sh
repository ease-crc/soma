#!/bin/bash
set -e

# FOR GITHUB RUNNER TO ACTUALLY USE THE CORRECT ONTOLOGY
if [ -d "/github/workspace" ]; then
    echo "Linking /github/workspace to /catkin_ws/src/ease_ontology"
    rm -rf "/catkin_ws/src/ease_ontology"
    ln -s "/github/workspace" "/catkin_ws/src/ease_ontology"
fi
# END FIX FOR PATHS

source "/opt/ros/$ROS_DISTRO/setup.bash"
if [ -f "/catkin_ws/devel/setup.bash" ]; then
    source "/catkin_ws/devel/setup.bash"
fi
exec "$@"
