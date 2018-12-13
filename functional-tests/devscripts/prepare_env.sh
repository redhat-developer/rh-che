#!/usr/bin/env bash

set -e
echo "Installing docker..."
yum install -y docker
systemctl start docker
docker pull quay.io/openshiftio/rhchestage-rh-che-functional-tests-dep

export HOST_URL=$HOST_URL
eval "$(./env-toolkit load -f jenkins-env.json -r  USERNAME PASSWORD EMAIL OFFLINE_TOKEN)"

mkdir logs
