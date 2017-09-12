#!/bin/bash
# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

if [ $(minishift status) != "Running" ]; then
  echo "The Minishift VM should be running"
  exit 1
fi

commandDir=$(dirname "$0")

source ${commandDir}/../config
source ${commandDir}/env-for-minishift
export CHE_IMAGE_REPO=${DOCKER_HUB_NAMESPACE}/che-server

if [[ "$@" =~ "-DwithoutDashboard" ]]; then
  export CHE_IMAGE_TAG=nightly-${RH_DIST_SUFFIX}-no-dashboard
else
  export CHE_IMAGE_TAG=nightly-${RH_DIST_SUFFIX}
fi

bash ${commandDir}/build_fabric8.sh $*
if [ $? -ne 0 ]; then
  echo 'Build Failed!'
  exit 1
fi

bash ${commandDir}/openshift_deploy.sh --command cleanup
bash ${commandDir}/openshift_deploy.sh --command deploy
