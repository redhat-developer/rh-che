#!/bin/bash

# this script downloads runs the build
# to create the che binaries

. config 

mkdir $NPM_CONFIG_PREFIX
scl enable rh-maven33 rh-nodejs4 'mvn -B install -U'
if [ $? -ne 0 ]; then
  echo "Error building che/rh-che with dashboard"
  exit 1;
fi

scl enable rh-maven33 rh-nodejs4 'mvn -B --activate-profiles=-checkout-base-che -DwithoutDashboard clean install -U'
if [ $? -ne 0 ]; then
  echo "Error building che/rh-che without dashboard"
  exit 1;
fi
