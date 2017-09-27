#!/bin/bash
# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

export BuildUser=$USER
export DeveloperBuild="true"

currentDir=$(pwd)
scriptDir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"



cd ${scriptDir}/..
source config
export CHE_IMAGE_REPO=${DOCKER_HUB_NAMESPACE}/che-server
cd ${scriptDir}/../.ci

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
