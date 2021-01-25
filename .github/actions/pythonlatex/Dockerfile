FROM gokhlayeh/latex:v4

RUN dnf install  -y --setopt=install_weak_deps=False \
    python3-pip \
    && \
    python3 -m pip install --no-cache-dir \
    owlready2
