#!/bin/bash

export BuildUser=$USER
export DeveloperBuild="true"

currentDir=$(pwd)

cd $(dirname "$0")/..

source config
export CHE_IMAGE_REPO=${DOCKER_HUB_NAMESPACE}/che-server

if [[ "$@" =~ "-DwithoutDashboard" ]]; then
  export CHE_IMAGE_TAG=nightly-${RH_DIST_SUFFIX}-no-dashboard
else
  export CHE_IMAGE_TAG=nightly-${RH_DIST_SUFFIX}
fi

bash cico_build.sh $*
if [ $? -ne 0 ]; then
  echo 'Build Failed!'
  cd ${currentDir}
  exit 1
fi
cd ${currentDir}
