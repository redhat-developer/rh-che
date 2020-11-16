#!/usr/bin/env bash
# Copyright (c) 2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

set -e -m +x

function checkPullRequest() {
  PULL_REQUESTS=$(curl -s https://api.github.com/repos/redhat-developer/rh-che/pulls?state=open | jq '.[].title')
  PR_EXISTS=1
  while read -r pr_title
  do
    if [[ "${pr_title}" == "\"${RELATED_PR_TITLE}\"" ]]; then
      PR_EXISTS=0
      break
    fi
  done <<< "$PULL_REQUESTS"

  return $PR_EXISTS
}

function createPullRequest() {
  PR_BODY="Tracking changes for fixing compatibility with upstream ${CHE_VERSION}. This PR was created automatically by Jenkins from job ${JOB_NAME}"
  PR_BASE="master"
  echo "Pull request for tracking changes of version ${CHE_VERSION} was not found - creating new one."
  git push origin "${BRANCH}" -f
  curl -X POST -s -L -H "Content-Type: application/json" -u "${GITHUB_AUTH_STRING}" \
       --data "{\"title\":\"${RELATED_PR_TITLE}\", \"head\":\"${BRANCH}\", \"base\":\"${PR_BASE}\", \"body\":\"${PR_BODY}\"}" \
       https://api.github.com/repos/redhat-developer/rh-che/pulls
  return_code=$?
  if [ ! $return_code -eq 0 ]; then
    commentToPR "Can not create PR."
    setRedStatus
    echo $return_code > compatibility_status
    exit $return_code
  fi
}

function commentToPR() {
  job_url="https://ci.centos.org/job/devtools-rh-che-rh-che-compatibility-test-dev.rdu2c.fabric8.io/${BUILD_NUMBER}/console"
  message="$1 See more details here: ${job_url}"
  url=$(curl -s https://api.github.com/repos/redhat-developer/rh-che/pulls?state=open | jq ".[] | select(.title == \"${RELATED_PR_TITLE}\") | .url" | sed 's/pulls/issues/g')
  url=$(echo "${url}" | cut -d"\"" -f 2)
  url="${url}/comments"
  curl -X POST -s -L -u "${GITHUB_AUTH_STRING}" "${url}" -d "{\"body\": \"${message}\"}"
}

function commentToRunTest() {
  job_url="https://ci.centos.org/job/devtools-rh-che-rh-che-compatibility-test-dev.rdu2c.fabric8.io/${BUILD_NUMBER}/console"
  message="[test]"
  url=$(curl -s https://api.github.com/repos/redhat-developer/rh-che/pulls?state=open | jq ".[] | select(.title == \"${RELATED_PR_TITLE}\") | .url" | sed 's/pulls/issues/g')
  url=$(echo "${url}" | cut -d"\"" -f 2)
  url="${url}/comments"
  curl -X POST -s -L -u "${GITHUB_AUTH_STRING}" "${url}" -d "{\"body\": \"${message}\"}"
}

function setRedStatus() {
  setStatus "failure"
}

function setGreenStatus() {
  setStatus "success"
}

function setStatus() {
  state=$1
  target_url="https://ci.centos.org/job/devtools-rh-che-rh-che-compatibility-test-dev.rdu2c.fabric8.io/${BUILD_NUMBER}/console"
  description="Compatibility check"
  context="compatibility"

  JSON_STRING=$(jq -n \
    --arg state "$state" \
    --arg url "$target_url" \
    --arg desc "$description" \
    --arg cont "$context" \
    '{state: $state, target_url: $url, description: $desc, context: $cont}')
  git_commit=$(curl -X GET https://api.github.com/repos/redhat-developer/rh-che/commits/${BRANCH} | jq .sha | cut -d"\"" -f 2);
  url="https://api.github.com/repos/redhat-developer/rh-che/statuses/${git_commit}"
  curl -v -X POST -H "Content-Type: application/json" -H "Authorization: token ${GITHUB_PUSH_TOKEN}" --data "$JSON_STRING" $url
}

function runCompatibilityTest() {
  source ./config
  source ./.ci/cico_utils.sh

  echo "Installing dependencies:"
  installDependenciesForCompatibilityCheck

  export DEV_CLUSTER_URL=https://api.che-dev.x6e0.p1.openshiftapps.com:6443/
  CHE_VERSION=$(curl -s https://raw.githubusercontent.com/eclipse/che/master/pom.xml | xq -r '.project.version')
  export CHE_VERSION
  if [[ -z $CHE_VERSION ]]; then
    echo "FAILED to get che version. Finishing script."
    echo "1" > compatibility_status
    exit 1
  fi
  export RELATED_PR_TITLE="Update to ${CHE_VERSION%-SNAPSHOT}"
  export BRANCH="upstream-check-${CHE_VERSION}"

  echo "********** Prepare environment for running compatibility test with upstream version of che: ${CHE_VERSION} **********"

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

  echo "Checking credentials:"
  checkAllCreds

  echo "Configuring remote:"
  git remote set-url --push origin "https://${GITHUB_AUTH_STRING}@github.com/redhat-developer/rh-che.git"

  #Check branch for tracking changes/Create new branch
  echo "Checking if branch ${BRANCH} exists."
  set +e
  git checkout "${BRANCH}" > /dev/null 2>&1
  return_code=$?
  set -e
  if [ ! $return_code -eq 0 ]; then
    echo "Branch ${BRANCH} not found - creating new one."
    git checkout master > /dev/null
    git reset --hard origin/master
    git checkout -b "${BRANCH}"
    echo "Setting new branch origin"
    git push --set-upstream origin "${BRANCH}"
    echo ">>> change upstream version to: ${CHE_VERSION}"
    mvn versions:set-property -Dproperty=che.version -DnewVersion=${CHE_VERSION}
    mvn versions:update-parent versions:commit -DallowSnapshots=true -DparentVersion=[${CHE_VERSION}] -U
  fi

  if ( git diff --exit-code ); then
    echo "Nothing to commit, continue."
  else
    echo "Changes found. Commit and push them before creating PR."
    git add -u
    git commit -m "Changing version of parent che to ${CHE_VERSION}"
    git push origin "${BRANCH}" -f
    return_code=$?
    if [ ! $return_code -eq 0 ]; then
      echo $return_code > compatibility_status
      exit $return_code
    fi
  fi

  set +e
  git rebase origin/master "${BRANCH}"
  rebase_return_code=$?
  set -e

  if [ ! $rebase_return_code -eq 0 ]; then
    echo "Rebase failed with code: $rebase_return_code"
    echo "Aborting rebase..."
    rebase --abort
  fi

  if ! checkPullRequest; then
      #If PR doesn't exist
      echo "PR does not exists. Creating PR."
      createPullRequest
  fi

  #if rebase was not successful, send message to the PR and quit
  if [ ! $rebase_return_code -eq 0 ]; then
    echo "There banch ${BRANCH} can't be rebased. Please solve conflicts and rebase a branch. Sending comment to related PR."
    message="Periodic compatibility check failed to rebase a branch ${BRANCH}. Please, resolve conflicts and rebase the branch."
    commentToPR "$message"
    setRedStatus
    echo $rebase_return_code > compatibility_status
    exit $rebase_return_code
  fi

  #rebase was successful, push rebased branch
  git push origin "${BRANCH}" -f

  #comment [test] to run tests
  commentToRunTest
  echo "0" > compatibility_status

}

#variable USE_CHE_LATEST_SNAPSHOT enables to use rhel-rhchestage-rh-che-automation image instead of rhel-rhchestage-rh-che-server
export USE_CHE_LATEST_SNAPSHOT="true"
run_tests_timeout_seconds=${RUN_TEST_TIMEOUT:-2400}
# SECONDS is an internal bash variable that is increased by one every second that a shell is executing a command/script
# It is being reset here to zero to be used as a counter for our timeout
SECONDS=0
touch compatibility_status
echo "Starting compatibility test"
runCompatibilityTest &
COMPATIBILITY_PID=$!
COMPATIBILITY_STATUS=$(cat compatibility_status)
echo "Compatibility test running in background thread ${COMPATIBILITY_PID} ${COMPATIBILITY_STATUS}"
while true; do
  COMPATIBILITY_STATUS=$(cat compatibility_status)
  echo "Tick Tock ${SECONDS}"
  if [[ "${COMPATIBILITY_STATUS}" != "" ]]; then
    echo "Compatibility test finished with exit code $COMPATIBILITY_STATUS"
    kill "$COMPATIBILITY_PID" > /dev/null 2>&1
    rm compatibility_status
    exit "$COMPATIBILITY_STATUS"
  fi
  if [ "$SECONDS" -gt "$run_tests_timeout_seconds" ]; then
    echo "Compatibility test timed out"
    setRedStatus
    kill "$COMPATIBILITY_PID" > /dev/null 2>&1
    rm compatibility_status
    exit 1
  fi
  sleep 10
done
