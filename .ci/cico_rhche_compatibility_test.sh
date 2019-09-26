#!/usr/bin/env bash
# Copyright (c) 2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

set -e -m +x

function checkPullRequest() {
  PULL_REQUESTS=$(curl -s https://api.github.com/repos/redhat-developer/rh-che/pulls?state=open | jq '.[].title')
  PR_EXISTS="false"
  while read -r pr_title
  do
    if [[ "$pr_title" == "\"$RELATED_PR_TITLE\"" ]]; then
      PR_EXISTS="true"
      break
    fi
  done <<< "$PULL_REQUESTS"

  if [[ "$PR_EXISTS" == "false" ]]; then
    echo "false"
  else
    echo "true"
  fi
}

function createPullRequest() {
  PR_BODY="Tracking changes for fixing compatibility with upstream $CHE_VERSION. This PR was created automatically by Jenkins from job $JOB_NAME"
  PR_BASE="master"
  echo "Pull request for tracking changes of version $CHE_VERSION was not found - creating new one."
  git push origin "$BRANCH" -f
  curl -X POST -s -L -H "Content-Type: application/json" -u "${GITHUB_AUTH_STRING}" \
       --data "{\"title\":\"$RELATED_PR_TITLE\", \"head\":\"$BRANCH\", \"base\":\"$PR_BASE\", \"body\":\"$PR_BODY\"}" \
       https://api.github.com/repos/redhat-developer/rh-che/pulls
  return_code=$?
  if [ ! $return_code -eq 0 ]; then
    echo $return_code > compatibility_test
    exit $return_code
  fi
}

function runCompatibilityTest() {
  export USE_CHE_LATEST_SNAPSHOT="true"
  export BASEDIR=$(pwd)
  export DEV_CLUSTER_URL=https://devtools-dev.ext.devshift.net:8443/
  export CHE_VERSION=$(curl -s https://raw.githubusercontent.com/eclipse/che/master/pom.xml | grep "^    <version>.*</version>$" | awk -F'[><]' '{print $3}')
  if [[ -z $CHE_VERSION ]]; then
    echo "FAILED to get che version. Finishing script."
    echo "1" > compatibility_test
    exit 1
  fi
  export RELATED_PR_TITLE="Update to $(echo $CHE_VERSION | cut -d'-' -f 1)"
  export BRANCH="upstream-check-$CHE_VERSION"

  echo "********** Prepare environment for running compatibility test with upstream version of che: $CHE_VERSION **********"

  eval "$(./env-toolkit load -f jenkins-env.json -r \
          ^DEVSHIFT_TAG_LEN$ \
          ^QUAY_ \
          ^KEYCLOAK \
          ^BUILD_NUMBER$ \
          ^JOB_NAME$ \
          ^ghprbPullId$ \
          ^RH_CHE \
          ^GITHUB \
          ^FABRIC8_HUB_TOKEN)"

  export GITHUB_AUTH_STRING=${GITHUB_PUSH_TOKEN}:${GITHUB_TOKEN_PASSWORD}

  source ./config
  source ./.ci/functional_tests_utils.sh

  echo "Checking credentials:"
  checkAllCreds
  echo "Installing dependencies:"
  installDependencies

  echo "Configuring remote:"
  git remote set-url origin https://${GITHUB_AUTH_STRING}@github.com/redhat-developer/rh-che.git

  #Check branch for tracking changes/Create new branch
  echo "Checking if branch $BRANCH exists."
  set +e
  git checkout "$BRANCH" 2>&1 >/dev/null
  return_code=$?
  set -e
  if [ ! $return_code -eq 0 ]; then
    echo "Branch $BRANCH not found - creating new one."
    git checkout master
    git checkout -b "$BRANCH"
    echo "Setting new branch origin"
    git push --set-upstream origin "$BRANCH"
    echo ">>> change upstream version to: $CHE_VERSION"
    scl enable rh-maven33 "mvn versions:update-parent  versions:commit -DallowSnapshots=true -DparentVersion=[${CHE_VERSION}] -U"
  fi

  if ( git diff --exit-code ); then
    echo "Nothing to commit, continue."
  else
    echo "Changes found. Commit and push them before creating PR."
    git add -u
    git commit -m "Changing version of parent che to $CHE_VERSION"
    git push origin "$BRANCH" -f
    return_code=$?
    if [ ! $return_code -eq 0 ]; then
      echo $return_code > compatibility_test
      exit $return_code
    fi
  fi

  git rebase origin/master "$BRANCH"
  return_code=$?
  if [ ! $return_code -eq 0 ]; then
    echo $return_code > compatibility_test
    exit $return_code
  else
    if [[ "$(checkPullRequest)" == "false" ]]; then
      #If PR doesn't exist
      createPullRequest
    fi
  fi

  echo "Setting image tags for pushing to quay."
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

  echo "********** Environment is set. Running build, deploy to dev cluster and tests. **********"
  set +e
  .ci/cico_build_deploy_test_rhche.sh
  return_code=$?
  set -e

  echo " --- After build-deploy-test phase. Result status is: $return_code --- "

  #if test fails, send comment to PR
  if [ $return_code != 0 ]; then
    echo "There were some problems and compatibility check failed. Sending comment to related PR."
    url=$(curl -s https://api.github.com/repos/redhat-developer/rh-che/pulls?state=open | jq ".[] | select(.title == \"$RELATED_PR_TITLE\") | .url" | sed 's/pulls/issues/g')
    url=$(echo $url | cut -d"\"" -f 2)
    url="${url}/comments"
    job_url="https://ci.centos.org/job/devtools-rh-che-rh-che-compatibility-test-dev.rdu2c.fabric8.io/$BUILD_NUMBER/console"
    message="Periodic compatibility check failed. See more details here: $job_url"
    curl -X POST -s -L -u ${GITHUB_AUTH_STRING} $url -d "{\"body\": \"$message\"}"
  fi

  echo $return_code > compatibility_status
}

run_tests_timeout_seconds=${RUN_TEST_TIMEOUT:-900}
SECONDS=0
touch compatibility_status
echo "Starting compatibility test"
runCompatibilityTest &
COMPATIBILITY_PID=$!
COMPATIBILITY_STATUS=$(cat compatibility_status)
echo "Compatibility test running in background thread $COMPATIBILITY_PID $COMPATIBILITY_STATUS"
while true; do
  COMPATIBILITY_STATUS=$(cat compatibility_status)
  echo "Tick Tock $SECONDS"
  if [[ "$COMPATIBILITY_STATUS" != "" ]]; then
    echo "Compatibility test finished"
    kill $COMPATIBILITY_PID 2>&1 >/dev/null
    rm compatibility_status
    exit $COMPATIBILITY_STATUS
  fi
  if [ $SECONDS -gt $run_tests_timeout_seconds ]; then
    echo "Compatibility test timed out"
    kill $COMPATIBILITY_PID 2>&1 >/dev/null
    rm compatibility_status
    exit 1
  fi
  sleep 1
done
