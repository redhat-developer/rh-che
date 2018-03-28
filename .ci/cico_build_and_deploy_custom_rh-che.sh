#!/usr/bin/env bash
# Copyright (c) 2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

set -x
set -e
set +o nounset

/usr/sbin/setenforce 0

export CHE_DOCKER_BASE_IMAGE=eclipse/che-server:nightly-centos
export ORIGIN_CLIENTS_URL=http://mirror.centos.org/centos/7/paas/x86_64/openshift-origin/origin-clients-3.7.1-2.el7.x86_64.rpm
export OPENSHIFT_RDU2C_URL=https://dev.rdu2c.fabric8.io:8443/
export OC_VERSION=3.9.19

function tagAndPushDocker() {
  export RH_CHE_AUTOMATION_BUILD_TAG=rh-che-automated-build-$(git rev-parse --short HEAD)
  echo "che-docker-base-image:${CHE_DOCKER_BASE_IMAGE}"
  docker pull ${CHE_DOCKER_BASE_IMAGE}
  docker tag ${CHE_DOCKER_BASE_IMAGE} eclipse/che-server:local
  rm -rf ./dockerfiles/che-fabric8/eclipse-che
  mkdir ./dockerfiles/che-fabric8/eclipse-che
  cp -r ./assembly/assembly-main/target/eclipse-che-fabric8-1.0.0-SNAPSHOT/eclipse-che-fabric8-1.0.0-SNAPSHOT/* ./dockerfiles/che-fabric8/eclipse-che/
  bash ./dockerfiles/che-fabric8/build.sh || exit 1
  docker rmi eclipse/che-server:local
  docker rmi ${CHE_DOCKER_BASE_IMAGE}
  docker tag eclipse/che-server:nightly rhcheautomation/che-server:${RH_CHE_AUTOMATION_BUILD_TAG}
  docker rmi eclipse/che-server:nightly
  set +x
  docker login -u ${RH_CHE_AUTOMATION_DOCKERHUB_USERNAME} -p ${RH_CHE_AUTOMATION_DOCKERHUB_PASSWORD}
  set -x
  docker push rhcheautomation/che-server:${RH_CHE_AUTOMATION_BUILD_TAG}
}

# Retrieve and test credentials

if [ ! -f "./jenkins-env" ]; then
  echo "CRITICAL ERROR: Jenkins env was not provided by jenkins job"
  exit 1
fi

set +x
grep -E "(KEYCLOAK|BUILD_NUMBER|JOB_NAME|RH_CHE)" ./jenkins-env | sed 's/^/export /g' | sed 's/= /=/g' > ./export_env_variables
if [ ! -f "./export_env_variables" ]; then
  echo "CRITICAL ERROR: sed edit of ./jeninks_env failed"
  exit 1
fi

source export_env_variables

echo "Running ${JOB_NAME} build number #${BUILD_NUMBER}, testing creds:"

CREDS_NOT_SET="false"
#curl ${ORIGIN_CLIENTS_URL} > origin-clients.rpm
#yum install --assumeyes ./origin-clients.rpm
curl "https://mirror.openshift.com/pub/openshift-v3/clients/${OC_VERSION}/linux/oc.tar.gz" | tar xvz -C /usr/local/bin
if [ -z ${RH_CHE_AUTOMATION_DOCKERHUB_USERNAME} ] || [ -z ${RH_CHE_AUTOMATION_DOCKERHUB_PASSWORD} ]; then
  echo "Dockerhub credentials not set"
  CREDS_NOT_SET="true"
fi
if [ -z ${RH_CHE_AUTOMATION_RDU2C_USERNAME} ] || [ -z ${RH_CHE_AUTOMATION_RDU2C_PASSWORD} ]; then
  echo "RDU2C credentials not set"
  CREDS_NOT_SET="true"
else
  oc login ${OPENSHIFT_RDU2C_URL} --insecure-skip-tls-verify \
                                  -u ${RH_CHE_AUTOMATION_RDU2C_USERNAME} \
                                  -p ${RH_CHE_AUTOMATION_RDU2C_PASSWORD} && echo "Credentials test OK" || {
    echo "Openshift login failed"
    echo "login: |${RH_CHE_AUTOMATION_RDU2C_USERNAME:0:1}*${RH_CHE_AUTOMATION_RDU2C_USERNAME:7:2}*${RH_CHE_AUTOMATION_RDU2C_USERNAME: -1}|" 
    echo "passwd: |${RH_CHE_AUTOMATION_RDU2C_PASSWORD:0:1}***${RH_CHE_AUTOMATION_RDU2C_PASSWORD: -1}|" 
    exit 1
  }
fi
if [ -z ${KEYCLOAK_TOKEN} ] || [ -z ${RH_CHE_AUTOMATION_CHE_PREVIEW_USERNAME} ] || [ -z ${RH_CHE_AUTOMATION_CHE_PREVIEW_PASSWORD} ]; then
  echo "Prod-preview credentials not set."
  CREDS_NOT_SET="true"
fi
if [ "${CREDS_NOT_SET}" == "true" ]; then
  echo "Failed to parse jenkins secure store credentials"
  exit 1
else
  echo "Credentials set successfully."
fi
set -x

# Getting core repos ready
rpm -iUvh https://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm
yum update --assumeyes
yum install python-pip --assumeyes

# Test and show version
pip -V

# Getting dependencies ready
yum install --assumeyes \
            docker \
            git \
            patch \
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

# Build rh-che
./.ci/cico_do_build_che.sh || exit 1

# Push image to docker registry
tagAndPushDocker

# Deploy rh-che image
set +x
./dev-scripts/deploy_custom_rh-che.sh -u ${RH_CHE_AUTOMATION_RDU2C_USERNAME} \
                                      -p ${RH_CHE_AUTOMATION_RDU2C_PASSWORD} \
                                      -r rhcheautomation/che-server \
                                      -t ${RH_CHE_AUTOMATION_BUILD_TAG} \
                                      -s
set -x

unset RH_CHE_AUTOMATION_BUILD_TAG;
unset CHE_DOCKER_BASE_IMAGE;
/usr/sbin/setenforce 1
