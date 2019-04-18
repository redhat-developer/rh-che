#!/usr/bin/env bash

set -e
echo "Installing docker..."
yum install -y docker | cat # Suppress multiple-line output for package installation
systemctl start docker
docker pull quay.io/openshiftio/rhchestage-rh-che-functional-tests-dep | cat # Suppress multiple-line output for docker pull

export HOST_URL=$HOST_URL
eval "$(./env-toolkit load -f jenkins-env.json -r  USERNAME PASSWORD EMAIL OFFLINE_TOKEN JOB_NAME BUILD_NUMBER)"

export OPENSHIFT_USERNAME=$USERNAME
export OPENSHIFT_PASSWORD=$PASSWORD

case $JOB_NAME in
	*2)
		export OPENSHIFT_URL="https://console.starter-us-east-2.openshift.com/"
		;;
	*1a)
		export OPENSHIFT_URL="https://console.starter-us-east-1a.openshift.com/"
		;;
	*1b)		
		export OPENSHIFT_URL="https://console.starter-us-east-1b.openshift.com/"
		;;
	*2a)
		export OPENSHIFT_URL="https://console.starter-us-east-2a.openshift.com/"
		;;
esac

mkdir logs

