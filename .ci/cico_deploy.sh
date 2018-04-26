#!/bin/bash
# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

set -u
set +e

ABSOLUTE_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Retrieve credentials to push the image to the docker hub
cat jenkins-env | grep -e PASS -e GIT -e DEVSHIFT > inherit-env
. inherit-env
. ${ABSOLUTE_PATH}/../config

# CHE_SERVER_DOCKER_IMAGE_TAG has to be set in che_image_tag.env file
. ~/che_image_tag.env

# Prepare config file
config_file=~/tests_config
echo "export OSO_MASTER_URL=https://api.starter-us-east-2.openshift.com:443" >> $config_file
echo "export OSO_NAMESPACE=mlabuda-jenkins" >> $config_file
echo "export OSO_USER=mlabuda" >> $config_file
echo "export OSO_DOMAIN=8a09.starter-us-east-2.openshiftapps.com" >> $config_file
echo "export OSO_HOSTNAME=mlabuda-jenkins.8a09.starter-us-east-2.openshiftapps.com" >> $config_file
echo "export CHE_SERVER_DOCKER_IMAGE_TAG=$CHE_SERVER_DOCKER_IMAGE_TAG" >> $config_file
echo "export NAMESPACE=${NAMESPACE}" >> $config_file

# Triggers update of tenant and execution of functional tests
echo "CHE VALIDATION: Verification skipped until job devtools-che-functional-tests get fixed"
# git clone https://github.com/redhat-developer/che-functional-tests.git
# mv $config_file che-functional-tests/config
# cp jenkins-env che-functional-tests/jenkins-env
# cd che-functional-tests
# . cico_run_EE_tests.sh
# echo "CHE VALIDATION: Verification passed. Pushing Che server image to prod registry."

echo "CHE VALIDATION: Pushing Che server image to prod registry."

STAGE_IMAGE_TO_PROMOTE="${REGISTRY}/${NAMESPACE}/${DOCKER_IMAGE}:${CHE_SERVER_DOCKER_IMAGE_TAG}"

if [ -n "${GIT_COMMIT}" -a -n "${DEVSHIFT_TAG_LEN}" ]; then
  TAG_SHORT_COMMIT_HASH=$(echo $GIT_COMMIT | cut -c1-${DEVSHIFT_TAG_LEN})
else
  echo "ERROR: GIT_COMMIT / DEVSHIFT_TAG_LEN env vars are not set. Aborting"
  exit 1
fi

if [ -n "${DEVSHIFT_USERNAME}" -a -n "${DEVSHIFT_PASSWORD}" ]; then
  docker login -u "${DEVSHIFT_USERNAME}" -p "${DEVSHIFT_PASSWORD}" ${REGISTRY}
else
  echo "ERROR: Can not push to registry.devshift.net: credentials are not set. Aborting"
  exit 1
fi

PROD_IMAGE_DEVSHIFT="${REGISTRY}/che/rh-che-server:${TAG_SHORT_COMMIT_HASH}"
PROD_IMAGE_DEVSHIFT_LATEST="${REGISTRY}/che/rh-che-server:latest"

echo "CHE VALIDATION: Pushing image ${PROD_IMAGE_DEVSHIFT} and ${PROD_IMAGE_DEVSHIFT_LATEST} to devshift registry"

docker tag "${STAGE_IMAGE_TO_PROMOTE}" "${PROD_IMAGE_DEVSHIFT}"
docker tag "${STAGE_IMAGE_TO_PROMOTE}" "${PROD_IMAGE_DEVSHIFT_LATEST}"

docker push "${PROD_IMAGE_DEVSHIFT}"
docker push "${PROD_IMAGE_DEVSHIFT_LATEST}"

echo "CHE VALIDATION: Image pushed to devshift registry"
