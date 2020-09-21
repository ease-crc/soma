#!/usr/bin/env sh

#Invoke this script to generate ELAN controlled vocabularies, or use the command below
#__author__ = "Mihai Pomarlan"
#__license__ = "GPL"
#__version__ = "1.0.0"
#__maintainer__ = "Mihai Pomarlan"
#__email__ = "pomarlan@uni-bremen.de"

SCRIPTPATH="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

python $SCRIPTPATH/elan_cv.py -of $SCRIPTPATH/SOMA.ecv -if $SCRIPTPATH/../owl/SOMA.owl $SCRIPTPATH/../owl/SOMA-OBJ.owl $SCRIPTPATH/../owl/SOMA-PROC.owl $SCRIPTPATH/../owl/SOMA-STATE.owl $SCRIPTPATH/../owl/SOMA-ACT.owl $SCRIPTPATH/../owl/SOMA-ELAN.owl $SCRIPTPATH/../owl/SOMA-HOME.owl -vi "lvl1-ease_tsd-motion" -ns "http://www.ease-crc.org/ont/SOMA.owl#" -sc Locomotion BodyMovement -vi "lvl1-ease_tsd-motion" -ns "http://www.ease-crc.org/ont/SOMA.owl#" -sc Manipulating Actuating -vi "lvl_2-ease_tsd-task_step" -ns 'http://www.ease-crc.org/ont/SOMA.owl#' -sc PhysicalTask -vi "think-aloud_coding" -ns "http://www.ease-crc.org/ont/SOMA.owl#" -sc ThinkAloudTopic -vi "task_step-object" -ns 'http://www.ease-crc.org/ont/SOMA.owl#' -sc DesignedTool DesignedContainer DesignedCoverer DesignedSupporter DesignedComponent DesignedFurniture BakedGood
