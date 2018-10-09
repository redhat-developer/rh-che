#!/bin/env bash
# Copyright (c) 2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

# ERROR CODES:
# 1 - missing credentials
# 2 - OpenShift login failed

export OC_VERSION=3.9.33
export DEV_CLUSTER_URL=https://devtools-dev.ext.devshift.net:8443/

yum install --assumeyes epel-release
yum install --assumeyes jq

eval "$(./env-toolkit load -f jenkins-env.json -r ^RH_CHE)"
curl -s "https://mirror.openshift.com/pub/openshift-v3/clients/${OC_VERSION}/linux/oc.tar.gz" | tar xvz -C /usr/local/bin
if [[ -z "${RH_CHE_AUTOMATION_RDU2C_USERNAME}" || -z "${RH_CHE_AUTOMATION_RDU2C_PASSWORD}" ]]; then
  echo "RDU2C credentials not set"
  exit 1
fi
if oc login ${DEV_CLUSTER_URL} --insecure-skip-tls-verify \
                               --token "${RH_CHE_AUTOMATION_DEV_CLUSTER_SA_TOKEN}";
then
  echo "OpenShift login successful"
else
  echo "OpenShift login failed"
  exit 2
fi

PullRequests=$(curl -s https://api.github.com/repos/redhat-developer/rh-che/pulls?state=open | jq '.[].number')
if [ $? -eq 0 ]; then
  echo "Getting list of open PRs successful."
else
  echo "Retrieving open pull requests failed with exit code $?"
  exit $?
fi
OCProjects=$(oc projects -q)
if [ $? -eq 0 ]; then
  echo "Getting list of OC projects successful."
else
  echo "Retrieving openshift projects failed with exit code $?"
  exit $?
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
