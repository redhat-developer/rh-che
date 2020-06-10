#!/usr/bin/env bash
# Copyright (c) 2020 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

set +x

eval "$(./env-toolkit load -f jenkins-env.json -r \
        ^BUILD_NUMBER$ \
        ^JOB_NAME$ \
        ^RH_CHE \
        ^CHE \
        USERNAME PASSWORD EMAIL \
        ghprbCommentBody)"

ACCOUNT_ENV="prod-preview"

source .ci/cico_utils.sh

echo "****** Starting prelease tests $(date) ******"
if [[ $JOB_NAME == *"saas-openshiftio"* ]]; then
    TEST_URL="https://che.prod-preview.openshift.io";  
else
    TEST_URL="${ghprbCommentBody%%"\r\n"*}";
fi

installDocker
installJQ

token=$(getActiveToken)
if [ -z $token ]; then
    echo "Can not obtain user token. Failing job."
fi
# Get version of tested Hosted Che
version=$(curl -s -X OPTIONS --header "Content-Type: application/json" --header "Authorization: Bearer ${token}" $TEST_URL/api/ | jq '.buildInfo')
version=${version//\"/}

if [ -z $version ]; then
    echo "Version is not set. Failing job."
    exit 1
fi

rhche_image="quay.io/openshiftio/rhchestage-rh-che-e2e-tests:${version}"

#reuse image if exists or build new image for tests
docker pull $rhche_image > /dev/null 2>&1
docker_pull_exit_code=$?

if [[ $docker_pull_exit_code == 0 ]]; then
    echo "Test image with version ${version} found. Reusing."
else
    echo "Could not found RH-Che tests image with tag ${version}."
    if [ $(curl -X GET https://quay.io/api/v1/repository/eclipse/che-e2e/tag/${version}/images | jq .status) == null ]; then
        echo "Upstream image with tag ${version} found. Building own RH-Che image based on Che image with ${version} tag."
        docker build --build-arg TAG=${version} -t e2e_tests dockerfiles/e2e-saas
        rhche_image=e2e_tests
    else
        echo "Could not found Che test image with tag ${version}. Building own RH-Che image based on Che image with nightly tag."
        docker build --build-arg TAG=nightly -t e2e_tests dockerfiles/e2e-saas
        rhche_image=e2e_tests
    fi
fi

#running tests
echo "Running devfile tests against $TEST_URL version $version"

path="$(pwd)"
docker run \
    -v $path/report:/tmp/rh-che/local_tests/report:Z \
    -v $path/e2e-saas/:/tmp/rh-che/local_tests:Z \
    -e USERNAME=$USERNAME \
    -e PASSWORD=$PASSWORD \
    -e URL=$TEST_URL \
    -e ACCOUNT_ENV=prod-preview \
    -e TEST_SUITE=pre-release \
    -e TS_SELENIUM_LOAD_PAGE_TIMEOUT=180000 \
    --shm-size=256m \
$rhche_image
RESULT=$?

mkdir -p ./rhche/${JOB_NAME}/${BUILD_NUMBER}/e2e_report
cp -r ./report ./rhche/${JOB_NAME}/${BUILD_NUMBER}/e2e_report

archiveArtifacts

if [[ $RESULT == 0 ]]; then
  echo "Tests result: SUCCESS"
else
  echo "Tests result: FAILURE"
fi

exit $RESULT	
