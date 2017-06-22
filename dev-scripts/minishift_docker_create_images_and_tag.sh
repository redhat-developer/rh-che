#!/bin/bash

export DeveloperBuild="true"

currentDir=$(pwd)

if [ $(minishift status) != "Running" ]; then
  echo "The Minishift VM should be running"
  exit 1
fi

commandDir=$(dirname "$0")

source ${commandDir}/env-for-minishift

cd ${commandDir}/..
bash ./cico_do_docker_build_tag_push.sh
if [ $? -ne 0 ]; then
  echo 'Build Failed!'
  cd ${currentDir}
  exit 1
fi
cd ${currentDir}
