#!/bin/bash

if [ $(minishift status) != "Running" ]; then
  echo "The Minishift VM should be running"
  exit 1
fi

commandDir = $(dirname "$0")

eval $(minishift docker-env)
bash ${commandDir}/build_fabric8.sh $*
if [ $? -ne 0 ]; then
  echo 'Build Failed!'
  exit 1
fi

source ${commandDir}/setenv-for-deploy.sh
oc rollout latest che -n eclipse-che
