#!/usr/bin/env bash
# Copyright (c) 2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

set -e

export USE_CHE_LATEST_SNAPSHOT="true"
export BASEDIR=$(pwd)
export DEV_CLUSTER_URL=https://devtools-dev.ext.devshift.net:8443/
CHE_VERSION=$(curl -s https://raw.githubusercontent.com/eclipse/che/master/pom.xml | grep "^    <version>.*</version>$" | awk -F'[><]' '{print $3}')
if [[ -z $CHE_VERSION ]]; then
	echo "FAILED to get che version. Finishing script."
	exit 1
fi

echo "********** Prepare environment for running compatibility test with upstream version of che: $CHE_VERSION **********"

eval "$(./env-toolkit load -f jenkins-env.json -r \
        ^DEVSHIFT_TAG_LEN$ \
        ^QUAY_ \
        ^KEYCLOAK \
        ^BUILD_NUMBER$ \
        ^JOB_NAME$ \
        ^ghprbPullId$ \
        ^RH_CHE \
        ^FABRIC8_HUB_TOKEN)"
        
source ./config
source ./.ci/functional_tests_utils.sh

echo "Checking credentials:"
checkAllCreds
echo "Installing dependencies:"
installDependencies

#Check branch for tracking changes/Create new branch
BRANCH="upstream-check-$CHE_VERSION"
set +e
git checkout "$BRANCH"
return_code=$?
set -e
if [ $return_code -eq 0 ]; then 
  echo "Branch $BRANCH found - rebasing."
  git rebase origin/master "$BRANCH" || echo "Unable to rebase on master - probably needs to resolve conflicts." && exit 1
else 
  echo "Branch $BRANCH not found - creating new one."
  git checkout -b "$BRANCH"
	
  #change version of used che
  echo ">>> change upstream version to: $CHE_VERSION"
  scl enable rh-maven33 rh-nodejs8 "mvn versions:update-parent  versions:commit -DallowSnapshots=true -DparentVersion=[${CHE_VERSION}] -U"
fi

#Get last commit short hash from upstream che
longHashUpstream=$(curl -s https://api.github.com/repos/eclipse/che/commits/master | jq .sha)
shortHashUpstream=${longHashUpstream:1:7}

#Get last commit short hash from rh-che branch 
longHashDownstream=$(git log | grep -m 1 commit | head -1 | cut -d" " -f 2)
shortHashDownstream=${longHashDownstream:0:7}

#DOCKER_IMAGE_TAG is used for running tests
#DOCKER_IMAGE_TAG_WITH_SHORTHASHES is used for tracking changes
export DOCKER_IMAGE_TAG="upstream-check-latest"	
export DOCKER_IMAGE_TAG_WITH_SHORTHASHES="upstream-check-$shortHashUpstream-$shortHashDownstream"
export PROJECT_NAMESPACE=compatibility-check

#set values needed for creating PR
RELATED_PR_TITLE="Update to $(echo $CHE_VERSION | cut -d'-' -f 1)"
PR_BODY="Tracking changes for fixing compatibility with upstream $CHE_VERSION. This PR was created automatically by Jenkins from job $JOB_NAME"
PR_HEAD="$BRANCH"
PR_BASE="master"

PULL_REQUESTS=$(curl -s https://api.github.com/repos/redhat-developer/rh-che/pulls?state=open | jq '.[].title')

#check if pull request exists
PR_EXISTS=1
while read -r pr_title
do
  if [[ "$pr_title" == "$RELATED_PR_TITLE" ]]; then
    echo "Pull request for tracking changes of version $CHE_VERSION has been already created."
    PR_EXISTS=0
    break
  fi
done <<< "$PULL_REQUESTS"

#if PR does not exist, create it
if [[ $PR_EXISTS -eq 1 ]]; then
  echo "Pull request for tracking changes of version $CHE_VERSION was not found - creating new one."
	
  #add changes and push branch
  if ( git diff --exit-code ); then
    echo "Nothing to commit, continue."
  else
    echo "Changes found. Commit and push them before creating PR."
    git add -u
    git commit -m"Changing version of parent che to $CHE_VERSION" || echo "No changes found to commit."
    curl -H "Authorization: token $(echo ${FABRIC8_HUB_TOKEN}|base64 --decode)" https://api.github.com/repos/redhat-developer/rh-che/
    git push origin "$BRANCH"
  fi
  #creating PR inspired in fabric8-services/fabric8-tenant cico_setup.sh
  curl -X POST -s -L -H "Content-Type: application/json" -H "Authorization: token $(echo ${FABRIC8_HUB_TOKEN}|base64 --decode)" --data '{"title":"$RELATED_PR_TITLE", "body":"$PR_BODY", "head":"$PR_HEAD", "base":"$PR_BASE"}' https://api.github.com/repos/redhat-developer/rh-che/pulls
else
  echo "Pull request for tracking changes of version $CHE_VERSION was found."
fi

set +e
echo "********** Environment is set. Running build, deploy to dev cluster and tests. **********"
.ci/cico_build_deploy_test_rhche.sh
RETURN_CODE=$?
set -e

#if test fails, send comment to PR
if [ $RETURN_CODE != 0 ]; then
  url=$(curl -s https://api.github.com/repos/redhat-developer/rh-che/pulls?state=open | jq '.[] | select(.title == "$RELATED_PR_TITLE") | .url' | sed 's/pulls/issues/g')
  url="${url}/comments"
  job_url="https://ci.centos.org/view/Devtools/job/devtools-rh-che-rh-che-compatibility-test-dev.rdu2c.fabric8.io/$BUILD_NUMBER/console"
  message="Periodic compatibility check failed. See more details here: $job_url"
  curl -X POST -s -L -H "Authorization: token $(echo ${FABRIC8_HUB_TOKEN}|base64 --decode)" $url -d "{\"body\": \"$message\"}"
fi

exit $RETURN_CODE
