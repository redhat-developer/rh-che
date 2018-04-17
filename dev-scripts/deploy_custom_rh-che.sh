#!/bin/bash
# Copyright (c) 2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

# ERROR CODES:
# 1 - general error
# 2 - missing file
# 3 - missing credentials
# 4 - OpenShift login failed
# 5 - command execution failed

usage="\\033[93;1m$(basename "$0") \\033[0;1m[-u <username>] [-p <passwd>] [-o <token>] \\033[90m{-t <tag>} {-r <registry>} {-e <namespace>} {-b <rh-che branch>} {-n} {-s} {-h} \\033[0m-- Login to dev cluster and deploy che with specific build tag

\\033[32;1mwhere:\\033[0m
    \\033[1m-u\\033[0m  \\033[93musername for openshift account\\033[0m
    \\033[1m-p\\033[0m  \\033[93mpassword for openshift account\\033[0m
    \\033[1m-o\\033[0m  \\033[93mopenshift token - \\033[31;1meither token or username and password must be provided\\033[0m
    \\033[1m-e\\033[0m  use specified namespace instead of the default one
    \\033[1m-b\\033[0m  use specified rh-che github branch
    \\033[1m-h\\033[0m  show this help text
    \\033[1m-n\\033[0m  do not delete files after script finishes
    \\033[1m-s\\033[0m  wipe sql database (postgres)
    \\033[1m-t\\033[0m  [\\033[1mdefault=latest\\033[0m] tag for specific build (first 7 characters of commit hash)
    \\033[1m-r\\033[0m  docker image registry from where to pull
    \\033[1m-z\\033[0m  run this script as a standalone self-contained application

\\033[32;1mrequirements\\033[0m:
    \\033[1moc\\033[0m  openshift origin CLI admin (dnf install origin-clients)
    \\033[1mjq\\033[0m  json CLI processor (dnf install jq (fedora) or brew install jq (macos))
    \\033[1myq\\033[0m  yaml CLI processor based on jq (pip install yq)"

export RH_CHE_DEPLOY_SCRIPT_CLEANUP="true";
export RH_CHE_WIPE_SQL="false";
export RH_CHE_IS_V_FIVE="false";
export RH_CHE_OPENSHIFT_USE_TOKEN="false";
export RH_CHE_OPENSHIFT_URL=https://dev.rdu2c.fabric8.io:8443;
export RH_CHE_JDBC_USERNAME=pgche;
export RH_CHE_JDBC_PASSWORD=pgchepassword;
export RH_CHE_JDBC_URL=jdbc:postgresql://postgres:5432/dbche;
export RH_CHE_RUNNING_STANDALONE_SCRIPT="false";

export RH_CHE_DOCKER_IMAGE_TAG="latest";
export RH_CHE_DOCKER_REPOSITORY="registry.devshift.net/che/rh-che-server";
export RH_CHE_GITHUB_BRANCH=master;

function unsetVars() {
  unset RH_CHE_OPENSHIFT_USERNAME;
  unset RH_CHE_OPENSHIFT_PASSWORD;
  unset RH_CHE_OPENSHIFT_TOKEN;
  unset RH_CHE_DOCKER_IMAGE_TAG;
  unset RH_CHE_OPENSHIFT_URL;
  unset RH_CHE_DOCKER_IMAGE_NAME;
  unset RH_CHE_DEPLOY_SCRIPT_CLEANUP;
  unset RH_CHE_WIPE_SQL;
  unset RH_CHE_PROJECT_NAMESPACE;
  unset RH_CHE_GITHUB_BRANCH;
  unset RH_CHE_JDBC_USERNAME;
  unset RH_CHE_JDBC_PASSWORD;
  unset RH_CHE_JDBC_URL;
  unset RH_CHE_RUNNING_STANDALONE_SCRIPT;
  unset RH_CHE_IS_V_FIVE;
  unset IMAGE_POSTGRES;
  unset RH_CHE_STATUS_PROGRESS;
  unset RH_CHE_STATUS_AVAILABLE;
}

function clearEnv() {
  rm rh-che.app.yaml > /dev/null 2>&1
  rm rh-che.config.yaml > /dev/null 2>&1
  rm -rf postgres > /dev/null 2>&1
}

function checkCheStatus() {
  RH_CHE_DEPLOYMENT_OC_STATUS=$(oc get dc rhche -o json)
  export RH_CHE_STATUS_PROGRESS=$(echo "$RH_CHE_DEPLOYMENT_OC_STATUS" | jq ".status.conditions | map(select(.type == \"Progressing\").status)[]")
  export RH_CHE_STATUS_AVAILABLE=$(echo "$RH_CHE_DEPLOYMENT_OC_STATUS" | jq ".status.conditions | map(select(.type == \"Available\").status)[]")
}

function waitForPostgresToBeDeleted() {
  oc delete dc postgres > /dev/null 2>&1
  printf "Waiting for postgres deployment to be deleted"
  while (oc get dc postgres > /dev/null 2>&1); do
    printf "."
    sleep 1
  done
  printf "\\nPostgres deployment successfully deleted.\\n"
}

function waitForCheToBeDeleted() {
  oc delete dc rhche > /dev/null 2>&1
  printf "Waiting for Rh-Che deployment to be deleted"
  while (oc get dc rhche > /dev/null 2>&1); do
    printf "."
    sleep 1
  done
  printf "\\nRh-Che deployment successfully deleted.\\n"
}

function deployPostgres() {
  echo -e "\\033[1mDeploying PostgreSQL\\033[0;38;5;60m"
  oc new-app -f postgres/postgres-template.yaml > /dev/null 2>&1
  if ! (oc get dc postgres > /dev/null 2>&1); then
    echo -e "\\033[0;91;1mFailed to deploy PostgreSQL\\033[0m"
    exit 5
  fi
  echo -e "\\033[0;92;1mPostreSQL database successfully deployed\\033[0m"
}

# Parse commandline flags
while getopts ':hnsu:p:r:t:o:e:b:z' option; do
  case "$option" in
    h) echo -e "$usage"
       exit 0
       ;;
    n) export RH_CHE_DEPLOY_SCRIPT_CLEANUP="false"
       ;;
    s) export RH_CHE_WIPE_SQL="true"
       ;;
    t) export RH_CHE_DOCKER_IMAGE_TAG=$OPTARG
       ;;
    u) export RH_CHE_OPENSHIFT_USERNAME=$OPTARG
       ;;
    p) export RH_CHE_OPENSHIFT_PASSWORD=$OPTARG
       ;;
    o) export RH_CHE_OPENSHIFT_TOKEN=$OPTARG
       export RH_CHE_OPENSHIFT_USE_TOKEN="true"
       ;;
    r) export RH_CHE_DOCKER_REPOSITORY=$OPTARG
       ;;
    e) export RH_CHE_PROJECT_NAMESPACE=$OPTARG
       ;;
    b) export RH_CHE_GITHUB_BRANCH=$OPTARG
       ;;
    z) export RH_CHE_RUNNING_STANDALONE_SCRIPT="true"
       ;; 
    :) echo -e "\\033[91;1mMissing argument for -$OPTARG\\033[0m" >&2
       echo -e "$usage" >&2
       unsetVars
       exit 1
       ;;
   \?) echo -e "\\033[91;1mIllegal option: -$OPTARG\\033[0m" >&2
       echo -e "$usage" >&2
       unsetVars
       exit 1
       ;;
  esac
done
shift $((OPTIND - 1))

if [ "$RH_CHE_OPENSHIFT_USE_TOKEN" == "true" ]; then
  if [[ -z "$RH_CHE_OPENSHIFT_TOKEN" ]]; then
    echo -e "\\033[91;1mOpenshift token not provided."
    unsetVars
    echo -e "$usage" >&2
    exit 3
  fi
else
  if [[ -z "$RH_CHE_OPENSHIFT_USERNAME" ]]; then
    echo -e "\\033[91;1mOpenshift username not provided.\\033[0m"
    unsetVars
    echo -e "$usage" >&2
    exit 3
  fi
  if [[ -z "$RH_CHE_OPENSHIFT_PASSWORD" ]]; then
    echo -e "\\033[91;1mOpenshift password not provided.\\033[0m"
    unsetVars
    echo -e "$usage" >&2
    exit 3
  fi
fi

# LOGIN TO OPENSHIFT CONSOLE
echo -e "\\033[1mLogging in to openshift...\\033[0m";
if [ "$RH_CHE_OPENSHIFT_USE_TOKEN" == "true" ]; then
  if ! (oc login ${RH_CHE_OPENSHIFT_URL} --insecure-skip-tls-verify=true --token="$RH_CHE_OPENSHIFT_TOKEN" > /dev/null 2>&1); then
    echo -e "\\033[91;1mOpenshift login with token failed [$?]\\033[0m"
    exit 4
  fi
else
  if ! (oc login ${RH_CHE_OPENSHIFT_URL} --insecure-skip-tls-verify=true -u "$RH_CHE_OPENSHIFT_USERNAME" -p "$RH_CHE_OPENSHIFT_PASSWORD" > /dev/null 2>&1); then
    echo -e "\\033[91;1mOpenshift login failed [$?]\\033[0m"
    exit 4
  fi
fi
export RH_CHE_PROJECT_NAMESPACE=${RH_CHE_PROJECT_NAMESPACE:-$(oc whoami)-che6-automated}
echo -e "\\033[92;1mLogin successful, creating project \\033[34m$RH_CHE_PROJECT_NAMESPACE\\033[0;38;5;238m";

# CREATE PROJECT
if oc project "${RH_CHE_PROJECT_NAMESPACE}" > /dev/null 2>&1;
then
  echo "Switched to project ${RH_CHE_PROJECT_NAMESPACE}"
else
  echo "Switching to project failed, probably not exists [$?]. Creating..."
  oc new-project "${RH_CHE_PROJECT_NAMESPACE}" --display-name='RH-Che6 Automated Deployment' > /dev/null 2>&1
fi

if oc get project "${RH_CHE_PROJECT_NAMESPACE}" > /dev/null 2>&1;
then
  echo -e "\\033[92;1mProject created successfully.\\033[0;38;5;238m";
else
  echo -e "\\033[91;1mProject creation failed [$?].\\033[0m"
  exit 5
fi

# GET DEPLOYMENT SCRIPTS
echo -e "\\033[0;1mGetting deployment scripts...\\033[0m"

# GET POSTGRES TEMPLATE
mkdir postgres > /dev/null 2>&1
cd postgres || exit 1
export IMAGE_POSTGRES="eclipse/che-postgres"
if ! (curl -L0fs https://raw.githubusercontent.com/eclipse/che/master/deploy/openshift/templates/multi/postgres-template.yaml -o postgres-template.yaml > /dev/null 2>&1); then
  echo -e "\\033[93;1mFile postgres-template.yaml is missing.\\033[0m"
  rm postgres-template.yaml
fi
cd ../ || exit 1

# GET CHE-APP CONFIGS
if [ "${RH_CHE_RUNNING_STANDALONE_SCRIPT}" == "true" ]; then
  RH_CHE_APP="./rh-che.app.yaml"
  RH_CHE_CONFIG="./rh-che.config.yaml"
  if ! (curl -L0fs https://raw.githubusercontent.com/redhat-developer/rh-che/"${RH_CHE_GITHUB_BRANCH}"/openshift/rh-che.app.yaml -o "${RH_CHE_APP}" > /dev/null 2>&1); then
    echo -e "\\033[91;1mCould not download che app definition config!\\033[0m"
    exit 2
  fi
  if ! (curl -L0fs https://raw.githubusercontent.com/redhat-developer/rh-che/"${RH_CHE_GITHUB_BRANCH}"/openshift/rh-che.config.yaml -o "${RH_CHE_CONFIG}" > /dev/null 2>&1); then
    echo -e "\\033[91;1mCould not download che config yaml!\\033[0m"
    exit 2
  fi
else
  RH_CHE_APP="./../openshift/rh-che.app.yaml"
  RH_CHE_CONFIG="./../openshift/rh-che.config.yaml"
fi

echo -e "\\033[92;1mGetting deployment scripts done.\\033[0m"

# DEPLOY POSTRES
if [ "$RH_CHE_WIPE_SQL" == "true" ]; then
  if (oc get dc postgres > /dev/null 2>&1); then
    waitForPostgresToBeDeleted
  fi
  deployPostgres
else
  if ! (oc get dc postgres > /dev/null 2>&1); then
    deployPostgres
  fi
fi

if (oc get dc rhche > /dev/null 2>&1); then
  waitForCheToBeDeleted
fi

# APPLY CHE CONFIGMAP
CHE_CONFIG_YAML=$(yq ".\"data\".\"che-keycloak-realm\" = \"NULL\" | 
                      .\"data\".\"che-keycloak-auth-server-url\" = \"NULL\" | 
                      .\"data\".\"che-keycloak-use-nonce\" = \"false\" | 
                      .\"data\".\"che-keycloak-client-id\" = \"740650a2-9c44-4db5-b067-a3d1b2cd2d01\" | 
                      .\"data\".\"che-keycloak-oidc-provider\" = \"https://auth.prod-preview.openshift.io/api\" | 
                      .\"data\".\"keycloak-github-endpoint\" = \"https://auth.prod-preview.openshift.io/api/token?for=https://github.com\" | 
                      .\"data\".\"service.account.secret\" = \"\" | 
                      .\"data\".\"service.account.id\" = \"\" | 
                      .\"data\".\"che.jdbc.username\" = \"$RH_CHE_JDBC_USERNAME\" | 
                      .\"data\".\"che.jdbc.password\" = \"$RH_CHE_JDBC_PASSWORD\" | 
                      .\"data\".\"che.jdbc.url\" = \"$RH_CHE_JDBC_URL\" " ${RH_CHE_CONFIG})

CHE_CONFIG_YAML=$(echo "$CHE_CONFIG_YAML" | \
                  yq ".\"data\".\"che-host\" = \"rhche-$RH_CHE_PROJECT_NAMESPACE.dev.rdu2c.fabric8.io\" |
                      .\"data\".\"infra-bootstrapper-binary-url\" = \"https://rhche-$RH_CHE_PROJECT_NAMESPACE.dev.rdu2c.fabric8.io/agent-binaries/linux_amd64/bootstrapper/bootstrapper\" |
                      .\"data\".\"che-api\" = \"https://rhche-$RH_CHE_PROJECT_NAMESPACE.dev.rdu2c.fabric8.io/api\" |
                      .\"data\".\"che-websocket-endpoint\" = \"wss://rhche-$RH_CHE_PROJECT_NAMESPACE.dev.rdu2c.fabric8.io/api/websocket\" |
                      .\"metadata\".\"name\" = \"rhche\" ")

if ! (echo "$CHE_CONFIG_YAML" | oc apply -f - > /dev/null 2>&1); then
  echo -e "\\033[91;1mFailed to apply configmap [$?].\\033[0m"
  exit 5
fi
echo -e "\\033[92;1mChe config deployed on \\033[34m${RH_CHE_PROJECT_NAMESPACE}\\033[0m"

# PROCESS CHE APP CONFIG
CHE_APP_CONFIG_YAML=$(yq "" ${RH_CHE_APP})
CHE_APP_CONFIG_YAML=$(echo "$CHE_APP_CONFIG_YAML" | \
                      yq "(.parameters[] | select(.name == \"IMAGE\").value) |= \"$RH_CHE_DOCKER_REPOSITORY\" |
                          (.parameters[] | select(.name == \"IMAGE_TAG\").value) |= \"$RH_CHE_DOCKER_IMAGE_TAG\" | 
                          (.objects[] | select(.kind == \"DeploymentConfig\").spec.template.spec.containers[0].imagePullPolicy) |= \"Always\"")

CHE_APP_CONFIG_YAML=$(echo "$CHE_APP_CONFIG_YAML" | \
                      yq "(.objects[] | select(.kind == \"DeploymentConfig\").spec.template.spec.containers[0].env[] |
                           select(.name == \"CHE_OPENSHIFT_SERVICE__ACCOUNT_SECRET\").valueFrom) |= {\"configMapKeyRef\":{\"key\":\"service.account.secret\",\"name\":\"rhche\"}} |
                          (.objects[] | select(.kind == \"DeploymentConfig\").spec.template.spec.containers[0].env[] |
                           select(.name == \"CHE_OPENSHIFT_SERVICE__ACCOUNT_ID\").valueFrom) |= {\"configMapKeyRef\":{\"key\":\"service.account.id\",\"name\":\"rhche\"}} |
                          (.objects[] | select(.kind == \"DeploymentConfig\").spec.template.spec.containers[0].env[] |
                           select(.name == \"CHE_JDBC_PASSWORD\").valueFrom) |= {\"configMapKeyRef\":{\"key\":\"che.jdbc.password\",\"name\":\"rhche\"}} |
                          (.objects[] | select(.kind == \"DeploymentConfig\").spec.template.spec.containers[0].env[] |
                           select(.name == \"CHE_JDBC_URL\").valueFrom) |= {\"configMapKeyRef\":{\"key\":\"che.jdbc.url\",\"name\":\"rhche\"}} |
                          (.objects[] | select(.kind == \"DeploymentConfig\").spec.template.spec.containers[0].env[] |
                           select(.name == \"CHE_JDBC_USERNAME\").valueFrom) |= {\"configMapKeyRef\":{\"key\":\"che.jdbc.username\",\"name\":\"rhche\"}}")

if ! (echo "$CHE_APP_CONFIG_YAML" | oc process -f - | oc apply -f - > /dev/null 2>&1); then
  echo -e "\\033[91;1mFailed to process che config [$?]\\033[0m"
  exit 5
fi

CHE_STARTUP_TIMEOUT=300
while [[ "${RH_CHE_STATUS_PROGRESS}" != "\"True\"" || "${RH_CHE_STATUS_AVAILABLE}" != "\"True\"" ]] && [ ${CHE_STARTUP_TIMEOUT} -gt 0 ]; do
  sleep 1
  checkCheStatus
  echo -e "\\033[0;38;5;60mChe-server status: Available:\\033[0;1m$RH_CHE_STATUS_AVAILABLE \\033[0;38;5;60mProgressing:\\033[0;1m$RH_CHE_STATUS_PROGRESS \\033[0;38;5;60mTimeout:\\033[0;1m$CHE_STARTUP_TIMEOUT\\033[0m"
  CHE_STARTUP_TIMEOUT=$((CHE_STARTUP_TIMEOUT-1))
done
if [ ${CHE_STARTUP_TIMEOUT} == 0 ]; then
  echo -e "\\033[91;1mFailed to start che-server: timed out\\033[0m"
  exit 1
fi

oc annotate --overwrite=true route/rhche kubernetes.io/tls-acme=true
echo -e "\\033[92;1mSUCCESS: Rh-Che deployed on \\033[34mhttps://rhche-$RH_CHE_PROJECT_NAMESPACE.dev.rdu2c.fabric8.io/\\033[0m"

# CLEANUP
if [ "$RH_CHE_DEPLOY_SCRIPT_CLEANUP" = true ]; then
  echo -e "\\033[1mCleaning up.\\033[0m"
  clearEnv
fi
unsetVars
echo -e "\\033[92;1mDone.\\033[0m"
