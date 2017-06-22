#!/bin/bash

export DeveloperBuild="true"

currentDir=$(pwd)

cd $(dirname "$0")/..

bash ./cico_do_docker_build_tag_push.sh
if [ $? -ne 0 ]; then
  echo 'Build Failed!'
  cd ${currentDir}
  exit 1
fi
cd ${currentDir}
