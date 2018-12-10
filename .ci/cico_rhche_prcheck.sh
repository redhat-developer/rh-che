#!/usr/bin/env bash
# Copyright (c) 2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

export PR_CHECK_BUILD="true"
export BASEDIR=$(pwd)
export DEV_CLUSTER_URL=https://devtools-dev.ext.devshift.net:8443/
export OC_VERSION=3.10.85

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

export PROJECT_NAMESPACE=prcheck-${RH_PULL_REQUEST_ID}
export DOCKER_IMAGE_TAG="${RH_TAG_DIST_SUFFIX}"-"${RH_PULL_REQUEST_ID}"

echo "Running ${JOB_NAME} PR: #${RH_PULL_REQUEST_ID}, build number #${BUILD_NUMBER}"
.ci/cico_build_deploy_test_rhche.sh
