#!/bin/env bash
# Copyright (c) 2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

# ERROR CODES:
# 1 - missing credentials
# 2 - OpenShift login failed

set +x

export DEV_CLUSTER_URL=https://devtools-dev.ext.devshift.net:8443/

source ./.ci/functional_tests_utils.sh
installJQ
installOC

eval "$(./env-toolkit load -f jenkins-env.json -r ^RH_CHE)"

if [[ -z "${RH_CHE_AUTOMATION_DEV_CLUSTER_SA_TOKEN}" ]]; then
  echo "RDU2C credentials not set"
  exit 1
fi
if (oc login ${DEV_CLUSTER_URL} --insecure-skip-tls-verify \
                                --token "${RH_CHE_AUTOMATION_DEV_CLUSTER_SA_TOKEN}" >/dev/null 2>&1);
then
  echo "OpenShift login successful"
else
  echo "OpenShift login failed with exit code $?"
  exit 2
fi

PullRequests=$(curl -s https://api.github.com/repos/redhat-developer/rh-che/pulls?state=open | jq '.[].number')
result=$?
if [ $result -eq 0 ]; then
  echo "Getting list of open PRs successful."
else
  echo "Retrieving open pull requests failed with exit code $result"
  exit $result
fi
OCProjects=$(oc projects -q)
result=$?
if [ $? -eq 0 ]; then
  echo "Getting list of OC projects successful."
else
  echo "Retrieving openshift projects failed with exit code $result"
  exit $result
fi

while read -r oc_project
do
  if [[ "$oc_project" != "prcheck-"* ]]; then
    continue
  fi
  PR_PRESENT="false"
  while read -r pr_number
  do
    if [[ "$oc_project" == *"-$pr_number" ]]; then
      PR_PRESENT="true"
    fi
  done <<< "$PullRequests"
  if [[ "$PR_PRESENT" == "false" ]]; then
    echo "PR for project $oc_project not found, deleting."
    oc delete project $oc_project
  else
    echo "PR for project $oc_project still open"
  fi
done <<< "$OCProjects"
