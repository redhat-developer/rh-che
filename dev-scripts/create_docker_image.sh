#!/bin/bash
# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

set -e
set -u

SCRIPT_DIR=$(cd "$(dirname "$0")"; pwd)
REPO_DIR=${SCRIPT_DIR}/..

DOCKER_IMAGE=${1:-"fabric8/rh-che-server"}
echo Building Docker image: ${DOCKER_IMAGE}

rm -rf ${REPO_DIR}/dockerfiles/che-fabric8/eclipse-che
cp -r ${REPO_DIR}/assembly/assembly-main/target/eclipse-che-*/eclipse-che-* \
      ${REPO_DIR}/dockerfiles/che-fabric8/eclipse-che
docker build -t ${DOCKER_IMAGE} ${REPO_DIR}/dockerfiles/che-fabric8/
