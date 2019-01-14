#!/usr/bin/env bash

# Provides methods:
#   checkAllCreds
#   installDependencies
#   archiveArtifacts
source .ci/functional_tests_utils.sh

function printHelp {
	YELLOW="\\033[93;1m"
	WHITE="\\033[0;1m"
	GREEN="\\033[32;1m"
	NC="\\033[0m" # No Color
	
	echo -e "${YELLOW}$(basename "$0") ${WHITE}[-u <username>] [-p <passwd>] [-m <email>] [-r <url>]" 
	echo -e "\n${NC}Script for running functional tests against production or prod-preview environment."
	echo -e "${GREEN}where:${WHITE}"
	echo -e "-u    username for openshift account"
	echo -e "-p    password for openshift account"
	echo -e "-m    email for openshift account"
	echo -e "-r    URL of Rh-che"
	echo -e "${NC}All paramters are mandatory.\n"
}

while getopts "hu:p:m:o:r:" opt; do
  case $opt in
    h) printHelp
      exit 0
      ;;
    u) export USERNAME=$OPTARG
      ;;
    p) export PASSWORD=$OPTARG
      ;;
    m) export EMAIL=$OPTARG
      ;;
    o) export OFFLINE_TOKEN=$OPTARG
      ;;
    r) export HOST_URL=$OPTARG
      ;;
    \?)
      echo "\"$opt\" is an invalid option!"
      exit 1
      ;;
    :)
      echo "Option \"$opt\" needs an argument."
      exit 1
      ;;
  esac
done

if [[ -z $USERNAME || -z $PASSWORD || -z $EMAIL || -z $HOST_URL ]]; then
	echo "Please check if all credentials for user are set."
	exit 1
fi

if [[ "$HOST_URL" == "che.openshift.io" ]]; then
	echo "Running test with user $USERNAME against production environment."
	CHE_OSIO_AUTH_ENDPOINT="https://auth.openshift.io"

	docker run --name functional-tests-dep --privileged \
	           -v /var/run/docker.sock:/var/run/docker.sock \
	           -v /root/payload/logs:/root/logs \
	           -e "RHCHE_SCREENSHOTS_DIR=/root/logs/screenshots" \
	           -e "RHCHE_ACC_USERNAME=$USERNAME" \
	           -e "RHCHE_ACC_PASSWORD=$PASSWORD" \
	           -e "RHCHE_ACC_EMAIL=$EMAIL" \
	           -e "CHE_OSIO_AUTH_ENDPOINT=$CHE_OSIO_AUTH_ENDPOINT" \
	           -e "RHCHE_HOST_URL=$HOST_URL" \
           quay.io/openshiftio/rhchestage-rh-che-functional-tests-dep
    RESULT=$?
else
	echo "Running test with user $USERNAME against prod-preview environment."
	CHE_OSIO_AUTH_ENDPOINT="https://auth.prod-preview.openshift.io"

	docker run --name functional-tests-dep --privileged \
	           -v /var/run/docker.sock:/var/run/docker.sock \
	           -v /root/payload/logs:/root/logs \
	           -e "RHCHE_SCREENSHOTS_DIR=/root/logs/screenshots" \
	           -e "RHCHE_ACC_USERNAME=$USERNAME" \
	           -e "RHCHE_ACC_PASSWORD=$PASSWORD" \
	           -e "RHCHE_ACC_EMAIL=$EMAIL" \
	           -e "RHCHE_ACC_TOKEN=$OFFLINE_TOKEN" \
	           -e "CHE_OSIO_AUTH_ENDPOINT=$CHE_OSIO_AUTH_ENDPOINT" \
	           -e "RHCHE_OPENSHIFT_TOKEN_URL=https://sso.prod-preview.openshift.io/auth/realms/fabric8/broker" \
	           -e "RHCHE_HOST_URL=$HOST_URL" \
           quay.io/openshiftio/rhchestage-rh-che-functional-tests-dep
    RESULT=$?
fi

archiveArtifacts

if [[ $RESULT == 0 ]]; then
	echo "Tests result: SUCCESS"
else
	echo "Tests result: FAILURE"
fi

exit $RESULT	
