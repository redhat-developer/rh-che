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

function BuildTagAndPushDocker() {
  echo "Docker status:"
  docker images
  .ci/cico_build.sh
  echo "After build:"
  docker images
}

set +x

if oc login ${DEV_CLUSTER_URL} --insecure-skip-tls-verify \
                               --token "${RH_CHE_AUTOMATION_DEV_CLUSTER_SA_TOKEN}";
then
  echo "OpenShift login successful"
else
  echo "OpenShift login failed"
  exit 3
fi

# Build and push image to docker registry
BuildTagAndPushDocker

# Deploy rh-che image
echo "Deploying image with tag ${DOCKER_IMAGE_TAG} from ${DOCKER_IMAGE_URL}"

#create project before deployment script to be able to set policy
if oc project "${PROJECT_NAMESPACE}" > /dev/null 2>&1;
then
  echo "Switched to project ${PROJECT_NAMESPACE}"
else
  echo "Switching to project failed, probably not exists [$?]. Creating..."
  oc new-project "${PROJECT_NAMESPACE}" --display-name='RH-Che6 Automated Deployment' > /dev/null 2>&1
fi
oc policy add-role-to-user edit Katka92 ScrewTSW rhopp garagatyi ibuziuk amisevsk davidfestal skabashnyuk -n $PROJECT_NAMESPACE

if ./dev-scripts/deploy_custom_rh-che.sh -o "${RH_CHE_AUTOMATION_DEV_CLUSTER_SA_TOKEN}" \
                                         -r "${DOCKER_IMAGE_URL}" \
                                         -t $DOCKER_IMAGE_TAG \
                                         -e $PROJECT_NAMESPACE \
                                         -S "$QUAY_SECRET_JSON" \
                                         -s \
                                         -U;
then
  echo "Che successfully deployed."
  export RH_CHE_AUTOMATION_SERVER_DEPLOYMENT_URL=http://rhche-$PROJECT_NAMESPACE.devtools-dev.ext.devshift.net/
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
