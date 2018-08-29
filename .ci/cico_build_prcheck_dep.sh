#!/usr/bin/env bash

# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

ABSOLUTE_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_PATH=${ABSOLUTE_PATH}/../dockerfiles/
REGISTRY="quay.io"
NAMESPACE=${NAMESPACE:-"rhchestage"}
TAG="latest"

cat jenkins-env \
    | grep -E '(QUAY)' \
    | sed 's/ //g' \
    | sed 's/^/export /g' \
    > /tmp/export-env
source /tmp/export-env

date '+DEP-TIMESTAMP: %d.%m.%Y - %H:%M:%S %Z'
yum update --assumeyes
yum install --assumeyes docker
systemctl start docker
date '+DEP-TIMESTAMP: %d.%m.%Y - %H:%M:%S %Z'

if [ -n "${QUAY_USERNAME}" -a -n "${QUAY_PASSWORD}" ]; then
  docker login -u "${QUAY_USERNAME}" -p "${QUAY_PASSWORD}" ${REGISTRY}
else
  echo "ERROR: Can not push to ${REGISTRY}: credentials are not set. Aborting"
  exit 1
fi

# Build and push PR-Check base image

DOCKERFILE="pr-check/"
DOCKER_IMAGE="rh-che-automation-dep"
DOCKER_IMAGE_URL="${REGISTRY}/openshiftio/${NAMESPACE}-${DOCKER_IMAGE}"

docker build -t ${DOCKER_IMAGE_URL}:${TAG} ${DOCKER_PATH}${DOCKERFILE}
if [ $? -ne 0 ]; then
  echo 'Docker Build Failed'
  exit 2
fi

date '+DEP-TIMESTAMP: %d.%m.%Y - %H:%M:%S %Z'
docker push ${DOCKER_IMAGE_URL}:${TAG}
date '+DEP-TIMESTAMP: %d.%m.%Y - %H:%M:%S %Z'

# Build and push functional-tests base image

DOCKERFILE="functional-tests/"
DOCKER_IMAGE="rh-che-functional-tests-dep"
DOCKER_IMAGE_URL="${REGISTRY}/openshiftio/${NAMESPACE}-${DOCKER_IMAGE}"

docker build -t ${DOCKER_IMAGE_URL}:${TAG} ${DOCKER_PATH}${DOCKERFILE}
if [ $? -ne 0 ]; then
  echo 'Docker Build Failed'
  exit 2
fi

date '+DEP-TIMESTAMP: %d.%m.%Y - %H:%M:%S %Z'
docker push ${DOCKER_IMAGE_URL}:${TAG}
date '+DEP-TIMESTAMP: %d.%m.%Y - %H:%M:%S %Z'
