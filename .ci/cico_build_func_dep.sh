#!/usr/bin/env bash

# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

set +x

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

source .ci/cico_utils.sh
yum update --assumeyes
installJava
installMvn
installDocker
installGit
SHORT_HASH=$(git rev-parse --short HEAD)
date '+DEP-TIMESTAMP: %d.%m.%Y - %H:%M:%S %Z'

if [ -n "${QUAY_USERNAME}" -a -n "${QUAY_PASSWORD}" ]; then
  docker login -u "${QUAY_USERNAME}" -p "${QUAY_PASSWORD}" ${REGISTRY}
else
  echo "ERROR: Can not push to ${REGISTRY}: credentials are not set. Aborting"
  exit 1
fi

# Build and push e2e-tests base image

DOCKERFILE="e2e-saas/"
DOCKER_IMAGE="rh-che-e2e-tests"
getMavenVersion
TAG=$(getVersionFromPom)
DOCKER_IMAGE_URL="${REGISTRY}/openshiftio/${NAMESPACE}-${DOCKER_IMAGE}"

echo "Building docker image for e2e tests (used e.g. in periodic tests)."
docker build -t ${DOCKER_IMAGE_URL}:${TAG} --build-arg TAG=$TAG ${DOCKER_PATH}${DOCKERFILE}
if [ $? -ne 0 ]; then
  echo 'Docker Build Failed'
  exit 2
fi

echo "Build was successful, pushing image ${DOCKER_IMAGE_URL} with tags ${TAG}, ${SHORT_HAS} and latest"
date '+DEP-TIMESTAMP: %d.%m.%Y - %H:%M:%S %Z'
docker tag ${DOCKER_IMAGE_URL}:${TAG} ${DOCKER_IMAGE_URL}:${SHORT_HASH}
docker push ${DOCKER_IMAGE_URL}:${TAG}
docker push ${DOCKER_IMAGE_URL}:latest
docker push ${DOCKER_IMAGE_URL}:${SHORT_HASH}
date '+DEP-TIMESTAMP: %d.%m.%Y - %H:%M:%S %Z'
