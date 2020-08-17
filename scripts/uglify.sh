#!/bin/sh

# A script to merge different modules of the
# EASE ontology into a bundled version.
# Author: Daniel Beßler

BASEDIR=$(dirname "$0")
OWL_FILE="$BASEDIR/../owl/SOMA-UGLY.owl"

rm -f "$OWL_FILE"
# roslaunch and remember PID
roslaunch soma uglify.launch &
roslaunch_PID=$!
# Wait until file is created
while [ ! -f "$OWL_FILE" ]; do sleep 1; done
# then kill the rolaunch process
kill -INT $roslaunch_PID
