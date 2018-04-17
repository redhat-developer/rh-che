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

yum install --assumeyes git

set +x
export CUSTOM_CHE_SERVER_FULL_URL=${RH_CHE_AUTOMATION_SERVER_DEPLOYMENT_URL}
export OSIO_USERNAME=${RH_CHE_AUTOMATION_CHE_PREVIEW_USERNAME}
export OSIO_PASSWORD=${RH_CHE_AUTOMATION_CHE_PREVIEW_PASSWORD}
echo "OSIO_USERNAME=${OSIO_USERNAME}" >> ./jenkins-env
echo "OSIO_PASSWORD=${OSIO_PASSWORD}" >> ./jenkins-env
echo "CUSTOM_CHE_SERVER_FULL_URL=${CUSTOM_CHE_SERVER_FULL_URL}" >> ./jenkins-env

echo "Downloading che-functional-tests repo"

git -c http.sslVerify=false clone https://github.com/redhat-developer/che-functional-tests.git
cp ./jenkins-env ./che-functional-tests/jenkins-env
mv ./artifacts.key ./che-functional-tests/artifacts.key
cd ./che-functional-tests

echo "Downloading done."
echo "Running functional tests against ${CUSTOM_CHE_SERVER_FULL_URL}"
set -x

DO_NOT_REBASE=true ./cico/cico_run_EE_tests.sh ./cico/config_rh_che_automated