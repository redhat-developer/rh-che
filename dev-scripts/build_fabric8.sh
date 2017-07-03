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

if [[ "$@" =~ "-DwithoutKeycloak=false" ]]; then
  keycloakSupport="WITH Keycloak support"
else
  keycloakSupport="WITHOUT Keycloak support"
fi

if [ -z ${UPSTREAM_CHE_PATH+x} ]; then 
    echo "!"
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    echo "!"
    echo "! Using dedicated Upstream Che repo CHECKED-OUT in the following target directory : "
    echo "!     $(pwd)/target/export/che-dependencies/che "
    echo "!"
    echo "! Building ${keycloakSupport}"
    echo "!"
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    echo "!"
    additionalArgument="-DwithoutKeycloak"
else 
    echo "!"
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    echo "!"
    echo "! Using Upstream Che repo from LOCAL directory : "
    echo "!     ${UPSTREAM_CHE_PATH} "
    echo "!"
    echo "! Building ${keycloakSupport}"
    echo "!"
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    echo "!"
    additionalArgument="-DwithoutKeycloak -DlocalCheRepository=${UPSTREAM_CHE_PATH}"
fi

bash cico_build.sh $additionalArgument $*
if [ $? -ne 0 ]; then
  echo 'Build Failed!'
  cd ${currentDir}
  exit 1
fi
cd ${currentDir}
