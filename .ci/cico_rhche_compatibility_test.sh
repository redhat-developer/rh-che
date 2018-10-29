#!/usr/bin/env bash
# Copyright (c) 2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

export USE_CHE_LATEST_SNAPSHOT="true"
export BASEDIR=$(pwd)
export DEV_CLUSTER_URL=https://devtools-dev.ext.devshift.net:8443/
export OC_VERSION=3.9.33

eval "$(./env-toolkit load -f jenkins-env.json -r \
        ^DEVSHIFT_TAG_LEN$ \
        ^QUAY_ \
        ^KEYCLOAK \
        ^BUILD_NUMBER$ \
        ^JOB_NAME$ \
        ^ghprbPullId$ \
        ^RH_CHE)"
        
source ./config
source .ci/prepare_env_utils.sh

echo "Checking credentials:"
checkAllCreds
echo "Installing dependencies:"
installDependencies

export DOCKER_IMAGE_TAG="upstream-check-latest"	
export PROJECT_NAMESPACE=compatibility-check

#change version of used che
CHE_VERSION=$(curl -s https://raw.githubusercontent.com/eclipse/che/master/pom.xml | grep "^    <version>.*</version>$" | awk -F'[><]' '{print $3}')
echo ">>> change upstream version to: $CHE_VERSION"
scl enable rh-maven33 rh-nodejs4 "mvn versions:update-parent  versions:commit -DallowSnapshots=true -DparentVersion=[${CHE_VERSION}] -U"

echo "Running compatibility check with build, deploy to dev cluster and test."
.ci/cico_build_deploy_test_rhche.sh
