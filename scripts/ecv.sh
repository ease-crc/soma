#!/usr/bin/env sh

#Invoke this script to generate ELAN controlled vocabularies, or use the command below
#__author__ = "Mihai Pomarlan"
#__license__ = "GPL"
#__version__ = "1.0.0"
#__maintainer__ = "Mihai Pomarlan"
#__email__ = "pomarlan@uni-bremen.de"

SCRIPTPATH="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

python $SCRIPTPATH/elan_cv.py -of $SCRIPTPATH/EASE.ecv -if $SCRIPTPATH/../owl/EASE.owl $SCRIPTPATH/../owl/EASE-OBJ.owl $SCRIPTPATH/../owl/EASE-PROC.owl $SCRIPTPATH/../owl/EASE-STATE.owl $SCRIPTPATH/../owl/EASE-ACT.owl -vi "lvl1-ease_tsd-motion" -ns "http://www.ease-crc.org/ont/EASE-PROC.owl#" -sc Locomotion BodyMovement -vi "lvl1-ease_tsd-motion" -ns "http://www.ease-crc.org/ont/EASE-ACT.owl#" -sc Manipulating Actuating -vi "lvl_2-ease_tsd-task_step" -ns 'http://www.ease-crc.org/ont/EASE-ACT.owl#' -sc PhysicalTask -vi "think-aloud_coding" -ns "http://www.ease-crc.org/ont/EASE-ACT.owl#" -sc ThinkAloudTopic

