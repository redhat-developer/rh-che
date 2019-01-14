#!/usr/bin/env bash

set -e
echo "Installing docker..."
yum install -y docker | cat # Suppress multiple-line output for package installation
systemctl start docker
docker pull quay.io/openshiftio/rhchestage-rh-che-functional-tests-dep | cat # Suppress multiple-line output for docker pull

export HOST_URL=$HOST_URL
eval "$(./env-toolkit load -f jenkins-env.json -r  USERNAME PASSWORD EMAIL OFFLINE_TOKEN JOB_NAME BUILD_NUMBER)"

mkdir logs
