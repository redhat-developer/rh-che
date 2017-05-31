#!/bin/bash

if [ $(minishift status) != "Running" ]; then
  echo "The Minishift VM should be running"
  exit 1
fi

commandDir=$(dirname "$0")

eval $(minishift docker-env)
bash ${commandDir}/build_fabric8.sh $*
if [ $? -ne 0 ]; then
  echo 'Build Failed!'
  exit 1
fi

source ${commandDir}/../config
export CHE_IMAGE_REPO=${DOCKER_HUB_NAMESPACE}/che-server

set -x

if [[ "$@" =~ "-DwithoutDashboard" ]]; then
  export CHE_IMAGE_TAG=nightly-${RH_DIST_SUFFIX}-no-dashboard
else
  export CHE_IMAGE_TAG=nightly-${RH_DIST_SUFFIX}
fi

set +x

source ${commandDir}/setenv-for-deploy.sh
bash ${commandDir}/delete-all.sh
bash ${commandDir}/create-all.sh
