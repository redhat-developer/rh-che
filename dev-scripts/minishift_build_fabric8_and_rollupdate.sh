#!/bin/bash

if [ $(minishift status) != "Running" ]; then
  echo "The Minishift VM should be running"
  exit 1
fi

commandDir=$(dirname "$0")

source ${commandDir}/env-for-minishift
bash ${commandDir}/build_fabric8.sh $*
if [ $? -ne 0 ]; then
  echo 'Build Failed!'
  exit 1
fi

oc rollout latest che -n ${CHE_OPENSHIFT_PROJECT}
