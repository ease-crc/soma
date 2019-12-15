#!/bin/bash
set -e
cd ./docs/NEEM-specification
make
mkdir -p ./build
mv ./neems.pdf ./build
