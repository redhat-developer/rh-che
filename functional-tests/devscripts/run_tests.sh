#!/usr/bin/env bash

# Provides methods:
#   checkAllCreds
#   installDependencies
#   archiveArtifacts

source .ci/functional_tests_utils.sh

echo "****** Starting functional tests $(date) ******"
start=$(date +%s)

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

if [[ "$JOB_NAME" == *"flaky"* ]]; then
	TEST_SUITE="flaky.xml"
elif [[ "$JOB_NAME" == *"saas"* ]]; then
	TEST_SUITE="saasPrCheck.xml"
else
	TEST_SUITE="simpleTestSuite.xml"
fi

#Get cluster to be able to get logs. Related to issue: https://github.com/redhat-developer/che-functional-tests/issues/476
if [[ "$USERNAME" == *"preview"* ]] || [[ "$PR_CHECK_BUILD" == "true" ]]; then
  API_SERVER_URL="https://api.prod-preview.openshift.io"
else
  API_SERVER_URL="https://api.openshift.io"
fi

events_file="events_report.txt"
touch $events_file

if [[ "$PR_CHECK_BUILD" == "true" ]]; then
  OC_CLUSTER_URL=$(curl -s -X GET --header 'Accept: application/json' "$API_SERVER_URL/api/users?filter\\[username\\]=$RH_CHE_AUTOMATION_CHE_PREVIEW_USERNAME" | jq '.data[0].attributes.cluster')
  OC_CLUSTER_URL="$(echo "${OC_CLUSTER_URL//\"/}")"
  oc login -u $RH_CHE_AUTOMATION_CHE_PREVIEW_USERNAME -p $RH_CHE_AUTOMATION_CHE_PREVIEW_PASSWORD $OC_CLUSTER_URL
  oc get events -n ${RH_CHE_AUTOMATION_CHE_PREVIEW_USERNAME}-che -w -o json > $events_file &
else
  OC_CLUSTER_URL=$(curl -s -X GET --header 'Accept: application/json' "$API_SERVER_URL/api/users?filter\\[username\\]=$USERNAME" | jq '.data[0].attributes.cluster')
  OC_CLUSTER_URL="$(echo "${OC_CLUSTER_URL//\"/}")"
  oc login -u $USERNAME -p $PASSWORD $OC_CLUSTER_URL
  oc get events -n ${USERNAME}-che -w -o json > $events_file &
fi

echo "API_SERVER_URL=$API_SERVER_URL"
echo "OPENSHIFT_URL=$OC_CLUSTER_URL"

#This format allows us to see username even if it is placed in Jenkins credential store. 
USERNAME_TO_PRINT="${USERNAME:0:3} ${USERNAME:3:${#USERNAME}}"
echo "User name printed in format: 3 first letters, space, the rest of letters.    $USERNAME_TO_PRINT"

#PR CHECK
if [[ "$PR_CHECK_BUILD" == "true" ]]; then
	HOST_URL=$(echo ${RH_CHE_AUTOMATION_SERVER_DEPLOYMENT_URL} | cut -d"/" -f 3)
	echo "Running test against developer cluster. URL: $HOST_URL"
	CHE_OSIO_AUTH_ENDPOINT="https://auth.prod-preview.openshift.io"
	path="$(pwd)"
	
	docker run --name functional-tests-dep --privileged \
	           -v /var/run/docker.sock:/var/run/docker.sock \
	           -v /root/payload/logs:/root/logs \
	           -v $path:/root/che/ \
	           -e "RHCHE_SCREENSHOTS_DIR=/root/logs/screenshots" \
	           -e "RHCHE_ACC_USERNAME=$RH_CHE_AUTOMATION_CHE_PREVIEW_USERNAME" \
	           -e "RHCHE_ACC_PASSWORD=$RH_CHE_AUTOMATION_CHE_PREVIEW_PASSWORD" \
	           -e "RHCHE_ACC_EMAIL=$RH_CHE_AUTOMATION_CHE_PREVIEW_EMAIL" \
	           -e "CHE_OSIO_AUTH_ENDPOINT=$CHE_OSIO_AUTH_ENDPOINT" \
	           -e "RHCHE_HOST_URL=$HOST_URL" \
	           -e "RHCHE_HOST_PROTOCOL=http" \
	           -e "RHCHE_PORT=80" \
	           -e "RHCHE_OPENSHIFT_TOKEN_URL=https://sso.prod-preview.openshift.io/auth/realms/fabric8/broker" \
	           -e "TEST_SUITE=prcheck.xml" \
	           -e "OPENSHIFT_URL=$OC_CLUSTER_URL" \
	           -e "OPENSHIFT_USERNAME=$RH_CHE_AUTOMATION_CHE_PREVIEW_USERNAME"  \
	           -e "OPENSHIFT_PASSWORD=$RH_CHE_AUTOMATION_CHE_PREVIEW_PASSWORD" \
           quay.io/openshiftio/rhchestage-rh-che-functional-tests-dep
    RESULT=$?
else
	if [[ -z $USERNAME || -z $PASSWORD || -z $EMAIL || -z $HOST_URL ]]; then
	    echo "Please check if all credentials for user are set."
	    exit 1
	fi
	
	#PRODUCTION
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
		           -e "TEST_SUITE=$TEST_SUITE" \
		           -e "OPENSHIFT_URL=$OC_CLUSTER_URL" \
		           -e "OPENSHIFT_USERNAME=$USERNAME"  \
		           -e "OPENSHIFT_PASSWORD=$PASSWORD" \
		           --shm-size=256m \
	           quay.io/openshiftio/rhchestage-rh-che-functional-tests-dep
    	RESULT=$?
    
	#PROD-PREVIEW
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
		           -e "CHE_OSIO_AUTH_ENDPOINT=$CHE_OSIO_AUTH_ENDPOINT" \
		           -e "RHCHE_OPENSHIFT_TOKEN_URL=https://sso.prod-preview.openshift.io/auth/realms/fabric8/broker" \
		           -e "RHCHE_HOST_URL=$HOST_URL" \
		           -e "TEST_SUITE=prodPreviewSuite.xml" \
		           -e "OPENSHIFT_URL=$OC_CLUSTER_URL" \
		           -e "OPENSHIFT_USERNAME=$USERNAME"  \
		           -e "OPENSHIFT_PASSWORD=$PASSWORD" \
		           --shm-size=256m \
	           quay.io/openshiftio/rhchestage-rh-che-functional-tests-dep
	    RESULT=$?
	fi
fi
end=$(date +%s)
test_duration=$(($end - $start))
echo "Running functional tests lasted $test_duration seconds."

if [[ "$TEST_SUITE" = "flaky.xml" ]]; then
	mkdir screens
	
	docker run 	\
	   -v $pwd/screens:/root/rh-che/e2e-saas/report:Z \
	   -e USERNAME=$RH_CHE_AUTOMATION_CHE_PREVIEW_USERNAME \
	   -e PASSWORD=$RH_CHE_AUTOMATION_CHE_PREVIEW_PASSWORD \
	   -e URL=https://che.openshift.io \
	   --shm-size=256m \
	quay.io/openshiftio/rhchestage-rh-che-e2e-tests           
		
	mkdir -p ./rhche/${JOB_NAME}/${BUILD_NUMBER}/e2e_report
	cp ./screens/ ./rhche/${JOB_NAME}/${BUILD_NUMBER}/e2e_report
	
	end=$(date +%s)
	test_duration=$(($end - $start))
	echo "Running e2e tests lasted $test_duration seconds."
fi

start=$(date +%s)
archiveArtifacts
end=$(date +%s)
archive_duration=$(($end - $start))
echo "Archiving artifacts lasted $archive_duration seconds."

if [[ $RESULT == 0 ]]; then
	echo "Tests result: SUCCESS"
else
	echo "Tests result: FAILURE"
fi

exit $RESULT	
