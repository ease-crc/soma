FROM osrf/ros:kinetic-desktop-full

RUN apt-get update \
    && apt-get install -y software-properties-common \
    && apt-add-repository ppa:swi-prolog/stable \
    && apt-get update \
    && apt-get install -y \
        swi-prolog \
        librrd-dev \
        libpython-dev \
        python-pip

COPY ros_entrypoint.sh /ros_entrypoint.sh
COPY .rosinstall /catkin_ws/src/.rosinstall
COPY .plrc /home/root/.plrc

WORKDIR /catkin_ws/src
RUN /ros_entrypoint.sh rosdep update \
    && /ros_entrypoint.sh wstool update -t . \
    && /ros_entrypoint.sh rosdep install -y --ignore-src --from-paths ./

WORKDIR /catkin_ws/src/genowl
RUN python -m pip install .

WORKDIR /catkin_ws
RUN /ros_entrypoint.sh catkin_make

ENV JAVA_HOME /usr/lib/jvm/default-java
ENV SWI_HOME_DIR /usr/lib/swi-prolog
ENV LD_LIBRARY_PATH /usr/lib/jvm/default-java/jre/lib/amd64:/usr/lib/jvm/default-java/jre/lib/amd64/server:$LD_LIBRARY_PATH

ENTRYPOINT ["/ros_entrypoint.sh"]
CMD ["rosrun", "ease_ontology", "uglify.sh"]
