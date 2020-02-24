#!/usr/bin/env bash
# Copyright (c) 2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#

#This script expects this environment variables set:
# CHE_TESTUSER_NAME, CHE_TESTUSER_PASSWORD, CHE_TESTUSER_EMAIL, RH_CHE_AUTOMATION_DEV_CLUSTER_SA_TOKEN

set +x

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
export RH_CHE_AUTOMATION_SERVER_DEPLOYMENT_URL=http://rhche-$PROJECT.devtools-dev.ext.devshift.net
export OPENSHIFT_TOKEN=$RH_CHE_AUTOMATION_DEV_CLUSTER_SA_TOKEN

function getStatus {
  response=$(curl -X GET -s \
    --header "Authorization: Bearer $USER_TOKEN" \
    $RH_CHE_AUTOMATION_SERVER_DEPLOYMENT_URL/api/workspace/$id)
  status=$(echo "$response" | jq .status)  
  echo $status
}

function workspaceWaitStatus {
  id=$1
  status_wanted=$2
  timeout=$3
  tick=$4
  ticks_to_end=$((timeout/tick))
  counter=0
  echo "Waiting for status $status_wanted for $timeout seconds. Tick: $tick Tick to end: $ticks_to_end";
  while [ $counter -lt $ticks_to_end ];  
  do
    status=$(getStatus)
    if [[ $status == "\"$status_wanted\"" ]]; then
      echo "Workspace is $status_wanted."
      return 0
    fi
    echo "   Workspace is not $status_wanted - waiting for $tick second. Actual status: $status"
    sleep $tick
    counter=$((counter + 1))
  done 
  return 1
}

function deleteWorkspace() {
  status=$(getStatus)
  #stop workspace is it is not already stopped
  if [[ $status != "\"STOPPED\"" ]]; then
    echo "Workspace is $status. Stopping workspace."
    curl -X DELETE --header "Authorization: Bearer $USER_TOKEN" $RH_CHE_AUTOMATION_SERVER_DEPLOYMENT_URL/api/workspace/$id/runtime
  fi

  workspaceWaitStatus $id "STOPPED" 30 2

  curl -X DELETE --header "Authorization: Bearer $USER_TOKEN" $RH_CHE_AUTOMATION_SERVER_DEPLOYMENT_URL/api/workspace/$id
  echo "Workspace removed. "
}

# --- TESTING CREDENTIALS ---
echo "Running ${JOB_NAME} build number #${BUILD_NUMBER}, testing creds:"

CREDS_NOT_SET="false"

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

#This format allows us to see username even if it is placed in Jenkins credential store. 
USERNAME_TO_PRINT="${CHE_TESTUSER_NAME:0:3} ${CHE_TESTUSER_NAME:3:${#CHE_TESTUSER_NAME}}"
echo "User name printed in format: 3 first letters, space, the rest of letters.    $USERNAME_TO_PRINT"


# --- INSTALLING NEEDED SOFTWARE ---
source ./.ci/functional_tests_utils.sh
installJQ
installOC
installYQ
installStartDocker
installMvn

ln -s /usr/local/bin/oc /tmp

oc login "${DEV_CLUSTER_URL}" --insecure-skip-tls-verify \
                               --token "${RH_CHE_AUTOMATION_DEV_CLUSTER_SA_TOKEN}";

oc new-project "${PROJECT}" --display-name="PR ${RH_PULL_REQUEST_ID} - Automated Deployment" > /dev/null 2>&1
oc policy add-role-to-user edit Katka92 ScrewTSW rhopp tomgeorge ibuziuk amisevsk davidfestal skabashnyuk -n $PROJECT
oc project $PROJECT

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

# --- GET USER ACTIVE TOKEN ---
length=${#CHE_TESTUSER_NAME}
echo "Trying to find token for $USERNAME_TO_PRINT"  
    
#verify environment - if production or prod-preview
#variable preview is used to differ between prod and prod-preview urls
rm -rf cookie-file loginfile.html
if [[ "$CHE_TESTUSER_NAME" == *"preview"* ]] || [[ "$CHE_TESTUSER_NAME" == *"saas"* ]]; then
  preview="prod-preview."
else
  preview=""
fi

response=$(curl -s -g -X GET --header 'Accept: application/json' "https://api.${preview}openshift.io/api/users?filter[username]=$CHE_TESTUSER_NAME")
data=$(echo "$response" | jq .data)
if [ "$data" == "[]" ]; then
  echo -e "${RED}Can not find active token for user $CHE_TESTUSER_NAME. Please check settings. ${NC}"
  exit 1
fi 
		
#get html of developers login page
curl -sX GET -L -c cookie-file -b cookie-file "https://auth.${preview}openshift.io/api/login?redirect=https://che.openshift.io" > loginfile.html

#get url for login from form
url=$(grep "form id" loginfile.html | grep -o 'http.*.tab_id=.[^\"]*')
dataUrl="username=$CHE_TESTUSER_NAME&password=$CHE_TESTUSER_PASSWORD&login=Log+in"
url=${url//\&amp;/\&}

#send login and follow redirects  
set +e
url=$(curl -w '%{redirect_url}' -s -X POST -c cookie-file -b cookie-file -d "$dataUrl" "$url")
found=$(echo "$url" | grep "token_json")

while true 
do
	url=$(curl -c cookie-file -b cookie-file -s -o /dev/null -w '%{redirect_url}' "$url")
	if [[ ${#url} == 0 ]]; then
		#all redirects were done but token was not found
		break
	fi
	found=$(echo "$url" | grep "token_json")
	if [[ ${#found} -gt 0 ]]; then
		#some redirects were done and token was found as a part of url
		break
	fi
done
set -e

#extract active token
token=$(echo "$url" | grep -o "ey.[^%]*" | head -1)
if [[ ${#token} -gt 0 ]]; then
  #save each token into file tokens.txt in format: token;username;["","prod-preview"]
	export USER_TOKEN=${token}
	echo "Token set successfully."
else
	echo -e "${RED}Failed to obtain token for $USERNAME! Probably user password is incorrect. Continue with other users. ${NC}"
	exit 1
fi

# --- CREATE AND START WORKSPACE ---
# create
echo "Creating and starting workspace..."

curl -Lso devfile.yaml https://che-devfile-registry.prod-preview.openshift.io/devfiles/nodejs/devfile.yaml

response=$(curl -X POST -s \
  --header 'Content-Type: text/yaml' \
  --header 'Accept: application/json' \
  --header "Authorization: Bearer $USER_TOKEN" \
  --data-binary "@devfile.yaml" \
  $RH_CHE_AUTOMATION_SERVER_DEPLOYMENT_URL/api/workspace/devfile?start-after-create=true)

status=$(echo "$response" | jq .status) 
if [ $status != "\"STOPPED\"" ]; then
  echo "Can not create workspace. Response:"
  echo "$response" 
  echo "Failing test. Project will not be removed."
  exit 1
else
  echo "Workspace created."
fi

#get id and remove surrounding quotation marks
id=$(echo "$response" | jq .id )
id=${id//\"}

# wait untill workspace is running
echo "Waiting for workspace to be STARTING";
if ! workspaceWaitStatus $id "STARTING" 10 1; then
  echo "Workspace didn't turned to STARTING state during 10 seconds. Failing tests."
  oc get events
  deleteWorkspace
  exit 1
fi

echo "Workspace is starting. Waiting for running.";
if ! workspaceWaitStatus $id "RUNNING" 120 5; then
  echo "Workspace is not running after 120 seconds. Failing tests."
  oc get events
  deleteWorkspace
  exit 1
fi

# --- ROLLOUT RH-CHE DEPLOYMENT ---
#get name of Che deployment
name=$(oc get dc | grep che | awk '{print $1}')

#get revision
revision=$(oc get dc | grep che | awk '{print $2}')

oc rollout latest "$name"

failed=0
counter=0
timeout=60
while [ $counter -lt $timeout ]; do
        revision2=$(oc get dc | grep che | awk '{print $2}')
        if [ "$revision" == "$revision2" ]; then
            counter=$((counter+1))
            echo "Revision was not updated yet. Waiting for 1 second and retrying."
            sleep 1
        else
          echo "Revision was update from $revision to $revision2."
          failed=1
          break
        fi
    done

echo $failed
if [[ $failed == 0 ]]; then
  echo "Revision was not updated. Rollout failed. Project was not deleted."
  deleteWorkspace
  exit 1
fi

# --- CHECK WORKSPACE IS RUNNING ---
if ! workspaceWaitStatus $id "RUNNING" 5 1; then
  echo "Workspace is not running after rollout. Failing test."  
  exit 1
fi

echo "Workspace is still running. Test passed. Removing workspace."

deleteWorkspace $id

echo "Deleting project ${PROJECT}."
oc delete project "${PROJECT}"
exit 0
