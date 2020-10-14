#!/bin/env bash
# Copyright (c) 2020 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

# ERROR CODES:
# 1 - Missing credentials
# 2 - OpenShift login failed

set +x

source ./.ci/cico_utils.sh
installOC

eval "$(./env-toolkit load -f jenkins-env.json -r ^RH_CHE)"

if [[ -z "${RH_CHE_DEV_CLUSTER_SERVER}" ]]; then
  echo "'che-dev' OSD v4 server is not set"
  exit 1
fi

if [[ -z "${RH_CHE_DEV_CLUSTER_TOKEN}" ]]; then
  echo "'che-dev' OSD v4 token is not set"
  exit 1
fi

if (oc login --token="${RH_CHE_DEV_CLUSTER_TOKEN}" --server="${RH_CHE_DEV_CLUSTER_SERVER}" >/dev/null 2>&1);
then
  WHO_AM_I=$(oc whoami)
  echo "OpenShift login successful: $WHO_AM_I"
else
  echo "OpenShift login failed with exit code $?. The token has expired"
  exit 2
fi
