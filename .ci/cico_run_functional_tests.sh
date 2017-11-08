#!/bin/bash
# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

set -u
set +e

ABSOLUTE_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# CHE_SERVER_DOCKER_IMAGE_TAG has to be set in che_image_tag.env file
. ~/che_image_tag.env


echo "CHE VALIDATION: Running che-functional-tests"
git clone https://github.com/redhat-developer/che-functional-tests.git
cp jenkins-env che-functional-tests/jenkins-env
sed -i "s/export CHE_SERVER_DOCKER_IMAGE_TAG=.*/export CHE_SERVER_DOCKER_IMAGE_TAG=${CHE_SERVER_DOCKER_IMAGE_TAG}/g" che-functional-tests/config
sed -i "s|export DOCKER_HUB_NAMESPACE=.*|export DOCKER_HUB_NAMESPACE=docker.io/rhchestage/che-server|g" che-functional-tests/config
cd che-functional-tests
export DO_NOT_REBASE=true
. cico_run_EE_tests.sh
echo "CHE VALIDATION: Che-functional tests passed."
