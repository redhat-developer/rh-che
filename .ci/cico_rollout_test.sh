#!/usr/bin/env bash
# Copyright (c) 2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#

#This script expects this environment variables set:
# CHE_TESTUSER_NAME, CHE_TESTUSER_PASSWORD, CHE_TESTUSER_EMAIL, RH_CHE_AUTOMATION_DEV_CLUSTER_SA_TOKEN

eval "$(./env-toolkit load -f jenkins-env.json -r \
        ^BUILD_NUMBER$ \
        ^JOB_NAME$ \
        ^RH_CHE \
        ^CHE)"
		
# --- SETTING ENVIRONMENT VARIABLES ---
export PROJECT=testing-rollout
export CHE_INFRASTRUCTURE=openshift
export CHE_MULTIUSER=true
export CHE_OSIO_AUTH_ENDPOINT=https://auth.prod-preview.openshift.io
export PROTOCOL=http
export OPENSHIFT_URL=https://devtools-dev.ext.devshift.net:8443
export RH_CHE_AUTOMATION_SERVER_DEPLOYMENT_URL=rhche-$PROJECT.devtools-dev.ext.devshift.net
export OPENSHIFT_TOKEN=$RH_CHE_AUTOMATION_DEV_CLUSTER_SA_TOKEN

# --- TESTING CREDENTIALS ---
echo "Running ${JOB_NAME} build number #${BUILD_NUMBER}, testing creds:"

CREDS_NOT_SET="false"

echo "test user name: $CHE_TESTUSER_NAME"

if [[ -z "${RH_CHE_AUTOMATION_DEV_CLUSTER_SA_TOKEN}" ]]; then
  echo "Developer cluster service account token is not set."
  CREDS_NOT_SET="true"
fi

if [[ -z "${CHE_TESTUSER_NAME}" || -z "${CHE_TESTUSER_PASSWORD}" ]]; then
  echo "Prod-preview credentials not set."
  CREDS_NOT_SET="true"
fi

if [ "${CREDS_NOT_SET}" = "true" ]; then
  echo "Failed to parse jenkins secure store credentials"
  exit 2
else
  echo "Credentials set successfully."
fi

# --- INSTALLING NEEDED SOFTWARE ---
source ./functional-tests-utils.sh
instalEpelRelease
installJQ
installOC
installYQ
installStartDocker

ln -s /usr/local/bin/oc /tmp

# --- DEPLOY RH-CHE ON DEVCLUSTER ---
if ./dev-scripts/deploy_custom_rh-che.sh -o "${RH_CHE_AUTOMATION_DEV_CLUSTER_SA_TOKEN}" \
                                         -e "${PROJECT}" \
                                         -z \
                                         -U;
then
  echo "Che successfully deployed."
else
  echo "Custom che deployment failed. Error code:$?"
  exit 4
fi

export OPENSHIFT_TOKEN=$RH_CHE_AUTOMATION_DEV_CLUSTER_SA_TOKEN
docker run --name functional-tests-dep --privileged \
		-v /var/run/docker.sock:/var/run/docker.sock \
		-v /tmp/oc/:/tmp/oc/ \
		-e "RHCHE_ACC_USERNAME=$CHE_TESTUSER_NAME" \
		-e "RHCHE_ACC_PASSWORD=$CHE_TESTUSER_PASSWORD" \
		-e "RHCHE_ACC_EMAIL=$CHE_TESTUSER_EMAIL" \
		-e "CHE_OSIO_AUTH_ENDPOINT=$CHE_OSIO_AUTH_ENDPOINT" \
		-e "TEST_SUITE=rolloutTest.xml" \
		-e "RHCHE_OPENSHIFT_TOKEN_URL=https://sso.prod-preview.openshift.io/auth/realms/fabric8/broker" \
		-e "RHCHE_HOST_PROTOCOL=http" \
		-e "RHCHE_HOST_URL=$RH_CHE_AUTOMATION_SERVER_DEPLOYMENT_URL" \
		-e "OPENSHIFT_URL=$OPENSHIFT_URL" \
		-e "OPENSHIFT_TOKEN=$OPENSHIFT_TOKEN" \
		-e "OPENSHIFT_PROJECT=$PROJECT" \
		quay.io/openshiftio/rhchestage-rh-che-functional-tests-dep
RESULT=$?

if [[ $RESULT == 0 ]]; then
	echo "Tests result: SUCCESS"
else
	echo "Tests result: FAILURE"
fi

exit $RESULT
