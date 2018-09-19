#!/usr/bin/env bash
# Copyright (c) 2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

# ERROR CODES:
# 1 - missing file
# 2 - missing credentials
# 3 - OpenShift login failed
# 4 - command execution failed

set -x
set -e
set +o nounset

/usr/sbin/setenforce 0

export BASEDIR=$(pwd)
export DEV_CLUSTER_URL=https://devtools-dev.ext.devshift.net:8443/
export OC_VERSION=3.9.33
export TARGET=${TARGET:-"rhel"}
export PR_CHECK_BUILD=${PR_CHECK_BUILD:-"true"}

function BuildTagAndPushDocker() {
  echo "Docker status:"
  docker images
  .ci/cico_build.sh
  echo "After build:"
  docker images
}

# Retrieve and test credentials
set +x
eval "$(./env-toolkit load -f jenkins-env.json -r \
        ^DEVSHIFT_TAG_LEN$ \
        ^QUAY_ \
        ^KEYCLOAK \
        ^BUILD_NUMBER$ \
        ^JOB_NAME$ \
        ^ghprbPullId$ \
        ^RH_CHE)"

echo "Running ${JOB_NAME} PR: #${ghprbPullId}, build number #${BUILD_NUMBER}, testing creds:"

CREDS_NOT_SET="false"
curl -s "https://mirror.openshift.com/pub/openshift-v3/clients/${OC_VERSION}/linux/oc.tar.gz" | tar xvz -C /usr/local/bin

if [[ -z "${QUAY_USERNAME}" && -z "${QUAY_PASSWORD}" ]]; then
  echo "Docker registry credentials not set"
  CREDS_NOT_SET="true"
fi

if [[ -z "${RH_CHE_AUTOMATION_RDU2C_USERNAME}" || -z "${RH_CHE_AUTOMATION_RDU2C_PASSWORD}" ]]; then
  echo "RDU2C credentials not set"
  CREDS_NOT_SET="true"
fi

if [[ -z "${KEYCLOAK_TOKEN}" || -z "${RH_CHE_AUTOMATION_CHE_PREVIEW_USERNAME}" || -z "${RH_CHE_AUTOMATION_CHE_PREVIEW_PASSWORD}" ]]; then
  echo "Prod-preview credentials not set."
  CREDS_NOT_SET="true"
fi

if [ "${CREDS_NOT_SET}" = "true" ]; then
  echo "Failed to parse jenkins secure store credentials"
  exit 2
else
  echo "Credentials set successfully."
fi

if oc login ${DEV_CLUSTER_URL} --insecure-skip-tls-verify \
                               -u "${RH_CHE_AUTOMATION_RDU2C_USERNAME}" \
                               -p "${RH_CHE_AUTOMATION_RDU2C_PASSWORD}";
then
  echo "OpenShift login successful"
else
  echo "OpenShift login failed"
  echo "login: |${RH_CHE_AUTOMATION_RDU2C_USERNAME:0:1}*${RH_CHE_AUTOMATION_RDU2C_USERNAME:7:2}*${RH_CHE_AUTOMATION_RDU2C_USERNAME: -1}|"
  echo "passwd: |${RH_CHE_AUTOMATION_RDU2C_PASSWORD:0:1}***${RH_CHE_AUTOMATION_RDU2C_PASSWORD: -1}|"
  exit 3
fi

set -x

# Getting core repos ready
yum install --assumeyes epel-release
yum update --assumeyes
yum install --assumeyes python-pip

# Test and show version
pip -V

# Getting dependencies ready
yum install --assumeyes \
            docker \
            git \
            patch \
            pcp \
            bzip2 \
            golang \
            make \
            jq \
            java-1.8.0-openjdk \
            java-1.8.0-openjdk-devel \
            centos-release-scl

yum install --assumeyes \
            rh-maven33 \
            rh-nodejs4

systemctl start docker
pip install yq

set +x
# Build and push image to docker registry
source ./config
BuildTagAndPushDocker

# Deploy rh-che image
echo "Deploying nightly-${RH_TAG_DIST_SUFFIX} from ${DOCKER_IMAGE_URL}"
if ./dev-scripts/deploy_custom_rh-che.sh -u "${RH_CHE_AUTOMATION_RDU2C_USERNAME}" \
                                         -p "${RH_CHE_AUTOMATION_RDU2C_PASSWORD}" \
                                         -r "${DOCKER_IMAGE_URL}" \
                                         -t "${RH_TAG_DIST_SUFFIX}"-"${RH_PULL_REQUEST_ID}" \
                                         -e prcheck-${RH_PULL_REQUEST_ID} \
                                         -S "$QUAY_SECRET_JSON" \
                                         -s \
                                         -U;
then
  echo "Che successfully deployed."
  export RH_CHE_AUTOMATION_SERVER_DEPLOYMENT_URL=http://rhche-prcheck-${RH_PULL_REQUEST_ID}.devtools-dev.ext.devshift.net/
else
  echo "Custom che deployment failed. Error code:$?"
  exit 4
fi
set -x

echo "Custom che deployment successful, running che-functional tests against ${RH_CHE_AUTOMATION_SERVER_DEPLOYMENT_URL}"
if ./.ci/cico_run_che-functional-tests.sh;
then
  echo "Functional tests finished without errors."
else
  echo "Che functional tests failed. Error code:$?"
  exit 4
fi

unset RH_CHE_AUTOMATION_BUILD_TAG;
unset CHE_DOCKER_BASE_IMAGE;
unset RH_CHE_AUTOMATION_SERVER_DEPLOYMENT_URL;
/usr/sbin/setenforce 1
