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

usage="\\033[93;1m$(basename "$0") \\033[0;1m[-u <username>] [-p <passwd>] [-o <token>] \\033[90m{-t <tag>} {-r <registry>} {-e <namespace>} {-b <rh-che branch>} {-n} {-s} {-R} {-V <che-version>} {-h} \\033[0m-- Login to dev cluster and deploy che with specific build tag

\\033[32;1mwhere:\\033[0m
    \\033[1m-u\\033[0m  \\033[93musername for openshift account\\033[0m
    \\033[1m-p\\033[0m  \\033[93mpassword for openshift account\\033[0m
    \\033[1m-o\\033[0m  \\033[93mopenshift token - \\033[31;1meither token or username and password must be provided\\033[0m
    \\033[1m-e\\033[0m  use specified namespace instead of the default one
    \\033[1m-b\\033[0m  use specified rh-che github branch
    \\033[1m-h\\033[0m  show this help text
    \\033[1m-n\\033[0m  do not delete files after script finishes
    \\033[1m-s\\033[0m  wipe sql database (postgres)
    \\033[1m-S\\033[0m  apply secret for oc
    \\033[1m-t\\033[0m  [\\033[1mdefault=latest\\033[0m] tag for specific build (first 7 characters of commit hash)
    \\033[1m-r\\033[0m  docker image registry from where to pull
    \\033[1m-z\\033[0m  run this script as a standalone self-contained application
    \\033[1m-U\\033[0m  use unsecure route (do not annotate route)
    \\033[1m-R\\033[0m  Deply curstom registries (flag)
    \\033[1m-V\\033[0m  Version of upstream Che the rh-che is based on (used for custom registries)

\\033[32;1mrequirements\\033[0m:
    \\033[1moc\\033[0m  openshift origin CLI admin (dnf install origin-clients)
    \\033[1mjq\\033[0m  json CLI processor (dnf install jq (fedora) or brew install jq (macos))
    \\033[1myq\\033[0m  yaml CLI processor based on jq (pip install yq)"

export RH_CHE_DEPLOY_SCRIPT_CLEANUP="true";
export RH_CHE_WIPE_SQL="false";
export RH_CHE_IS_V_FIVE="false";
export RH_CHE_OPENSHIFT_USE_TOKEN="false";
export RH_CHE_OPENSHIFT_URL=https://devtools-dev.ext.devshift.net:8443;
export RH_CHE_JDBC_USERNAME=pgche;
export RH_CHE_JDBC_PASSWORD=pgchepassword;
export RH_CHE_JDBC_URL=jdbc:postgresql://postgres:5432/dbche;
export RH_CHE_RUNNING_STANDALONE_SCRIPT="false";
export RH_CHE_USE_TLS="true"
export RH_CHE_APPLY_SECRET="false"

export RH_CHE_DOCKER_IMAGE_TAG="latest";
export RH_CHE_DOCKER_REPOSITORY="quay.io/openshiftio/che-rh-che-server";
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
  unset POSTGRES_STATUS_PROGRESS;
  unset POSTGRES_STATUS_AVAILABLE;
  unset RH_CHE_STATUS_PROGRESS;
  unset RH_CHE_STATUS_AVAILABLE;
  unset RH_CHE_USE_TLS;
  unset RH_CHE_APPLY_SECRET;
  unset RH_CHE_OC_SECRET;
  unset RH_CHE_USE_CUSTOM_REPOSITORIES;
  unset RH_CHE_CUSTOM_REPOSITORIES_VERSION;
}

function clearEnv() {
  rm rh-che.app.yaml > /dev/null 2>&1
  rm rh-che.config.yaml > /dev/null 2>&1
  rm che-plugin-registry.yaml > /dev/null 2>&1
  rm che-devfile-registry.yaml > /dev/null 2>&1
  rm -rf postgres > /dev/null 2>&1
}

function checkPostgresStatus() {
  POSTGRES_DEPLOYMENT_OC_STATUS=$(oc get dc postgres -o json)
  export POSTGRES_STATUS_PROGRESS=$(echo "$POSTGRES_DEPLOYMENT_OC_STATUS" | jq ".status.conditions | map(select(.type == \"Progressing\").status)[]")
  export POSTGRES_STATUS_AVAILABLE=$(echo "$POSTGRES_DEPLOYMENT_OC_STATUS" | jq ".status.conditions | map(select(.type == \"Available\").status)[]")
}

function checkCheStatus() {
  RH_CHE_DEPLOYMENT_OC_STATUS=$(oc get dc rhche -o json)
  export RH_CHE_STATUS_PROGRESS=$(echo "$RH_CHE_DEPLOYMENT_OC_STATUS" | jq ".status.conditions | map(select(.type == \"Progressing\").status)[]")
  export RH_CHE_STATUS_AVAILABLE=$(echo "$RH_CHE_DEPLOYMENT_OC_STATUS" | jq ".status.conditions | map(select(.type == \"Available\").status)[]")
}

function waitForPostgresToBeDeleted() {
  oc delete all -l app=postgres > /dev/null 2>&1
  oc delete pvc/postgres-data > /dev/null 2>&1
  printf "Waiting for postgres deployment to be deleted"
  while (oc get all -l app=postgres 2>&1 | [ "$(wc -l)" -ge 2 ] > /dev/null 2>&1); do
    printf "."
    sleep 1
  done
  printf "\\nPostgres deployment successfully deleted.\\n"
}

function waitForCheToBeDeleted() {
  oc delete all -l app=rhche > /dev/null 2>&1
  printf "Waiting for Rh-Che deployment to be deleted"
  while (oc get all -l app=rhche  2>&1 | [ "$(wc -l)" -ge 2 ] > /dev/null 2>&1); do
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

  POSTGRES_STARTUP_TIMEOUT=180
  while [[ "${POSTGRES_STATUS_PROGRESS}" != "\"True\"" || "${POSTGRES_STATUS_AVAILABLE}" != "\"True\"" ]] && [ ${POSTGRES_STARTUP_TIMEOUT} -gt 0 ]; do
    sleep 1
    checkPostgresStatus
    echo -e "\\033[0;38;5;60mPostgres status: Available:\\033[0;1m$POSTGRES_STATUS_AVAILABLE \\033[0;38;5;60mProgressing:\\033[0;1m$POSTGRES_STATUS_PROGRESS \\033[0;38;5;60mTimeout:\\033[0;1m$POSTGRES_STARTUP_TIMEOUT\\033[0m"
    POSTGRES_STARTUP_TIMEOUT=$((POSTGRES_STARTUP_TIMEOUT-1))
  done
  if [ ${POSTGRES_STARTUP_TIMEOUT} == 0 ]; then
    echo -e "\\033[91;1mFailed to start postgres: timed out\\033[0m"
    exit 1
  fi

  echo -e "\\033[0;92;1mPostreSQL database successfully deployed\\033[0m"
}

# Parse commandline flags
while getopts ':hnu:szUS:b:e:r:t:o:p:RV:' option; do
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
    U) export RH_CHE_USE_TLS="false"
       ;;
    S) export RH_CHE_APPLY_SECRET="true"
       export RH_CHE_OC_SECRET=$OPTARG
       ;;
    R) export RH_CHE_USE_CUSTOM_REPOSITORIES="true"
       ;;
    V) export RH_CHE_CUSTOM_REPOSITORIES_VERSION=$OPTARG
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
echo -e "Login successful, creating project $RH_CHE_PROJECT_NAMESPACE";

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

# Due to bug in dev-cluster, volume must be changed to emptyDir
# https://gitlab.cee.redhat.com/dtsd/housekeeping/issues/2570
if [ "$RH_CHE_OPENSHIFT_URL" == "https://devtools-dev.ext.devshift.net:8443" ]; then
  cp postgres-template.yaml postgres-template_tmp.yaml
  yq --yaml-output 'del(.objects[0].spec.template.spec.volumes[0].persistentVolumeClaim) | .objects[0].spec.template.spec.volumes[0] += {"emptyDir":{}}' postgres-template_tmp.yaml > postgres-template.yaml
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
  ABSOLUTE_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  RH_CHE_APP="${ABSOLUTE_PATH}/../openshift/rh-che.app.yaml"
  RH_CHE_CONFIG="${ABSOLUTE_PATH}/../openshift/rh-che.config.yaml"
fi

echo -e "\\033[92;1mGetting deployment scripts done.\\033[0m"

# DEPLOY CUSTOM REPOSITORIES
if [ "$RH_CHE_USE_CUSTOM_REPOSITORIES" == "true" ]; then
  echo "Downloading che plugin registry yaml"
  if ! (curl -L0fs https://raw.githubusercontent.com/eclipse/che-plugin-registry/master/deploy/openshift/che-plugin-registry.yml -o che-plugin-registry.yaml > /dev/null 2>&1); then
    echo -e "\\033[91;1mCould not download che-plugin-registry yaml!\\033[0m"
    exit 2
  fi
  echo "Downloading che devfile registry yaml"
  if ! (curl -L0fs https://raw.githubusercontent.com/eclipse/che-devfile-registry/master/deploy/openshift/che-devfile-registry.yaml -o che-devfile-registry.yaml > /dev/null 2>&1); then
    echo -e "\\033[91;1mCould not download che-devfile-registry yaml!\\033[0m"
    exit 2
  fi
  if [ ! -z "$RH_CHE_CUSTOM_REPOSITORIES_VERSION" ]; then
    echo "Processing che plugin registry yaml"
    yq --yaml-output ".parameters = (.parameters | map((select(.name == \"IMAGE_TAG\") | .value) |= \"${RH_CHE_CUSTOM_REPOSITORIES_VERSION}\"))" che-plugin-registry.yaml > che-plugin-registry-tmp.yaml
    mv che-plugin-registry-tmp.yaml che-plugin-registry.yaml
    echo "Processing che devfile registry yaml"
    yq --yaml-output ".parameters = (.parameters | map((select(.name == \"IMAGE_TAG\") | .value) |= \"${RH_CHE_CUSTOM_REPOSITORIES_VERSION}\"))" che-devfile-registry.yaml > che-devfile-registry-tmp.yaml
    mv che-devfile-registry-tmp.yaml che-devfile-registry.yaml
  fi
  echo "Deploy plugin registry"
  if ! (oc process -f che-plugin-registry.yaml | oc apply -f - > /dev/null 2>&1); then
    echo -e "\\033[0;91;1mFailed to deploy Che plugin registry\\033[0m"
    exit 5
  fi
  echo "Deploy devfile registry"
  if ! (oc process -f che-devfile-registry.yaml | oc apply -f - > /dev/null 2>&1); then
    echo -e "\\033[0;91;1mFailed to deploy Che devfile registry\\033[0m"
    exit 5
  fi  
fi

# DEPLOY POSTGRES
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

if [ "${RH_CHE_USE_TLS}" == "true" ]; then
  SECURE="s"
else
  SECURE=""
fi

# APPLY CHE CONFIGMAP
CHE_CONFIG_YAML=$(yq ".\"data\".\"CHE_KEYCLOAK_REALM\" = \"NULL\" |
                      .\"data\".\"CHE_KEYCLOAK_AUTH__SERVER__URL\" = \"NULL\" |
                      .\"data\".\"CHE_KEYCLOAK_USE__NONCE\" = \"false\" |
                      .\"data\".\"CHE_KEYCLOAK_CLIENT__ID\" = \"740650a2-9c44-4db5-b067-a3d1b2cd2d01\" |
                      .\"data\".\"CHE_KEYCLOAK_OIDC__PROVIDER\" = \"https://auth.prod-preview.openshift.io/api\" |
                      .\"data\".\"CHE_KEYCLOAK_GITHUB_ENDPOINT\" = \"https://auth.prod-preview.openshift.io/api/token?for=https://github.com\" |
                      .\"data\".\"service.account.secret\" = \"\" |
                      .\"data\".\"service.account.id\" = \"\" |
                      .\"data\".\"che.jdbc.username\" = \"$RH_CHE_JDBC_USERNAME\" |
                      .\"data\".\"che.jdbc.password\" = \"$RH_CHE_JDBC_PASSWORD\" |
                      .\"data\".\"che.jdbc.url\" = \"$RH_CHE_JDBC_URL\" |
                      .\"data\".\"CHE_LOG_LEVEL\" = \"INFO\" |
                      .\"data\".\"CHE_LOGS_APPENDERS_IMPL\" = \"plaintext\" " ${RH_CHE_CONFIG})

CHE_CONFIG_YAML=$(echo "$CHE_CONFIG_YAML" | \
                  yq ".\"data\".\"CHE_HOST\" = \"rhche-$RH_CHE_PROJECT_NAMESPACE.devtools-dev.ext.devshift.net\" |
                      .\"data\".\"CHE_INFRA_KUBERNETES_BOOTSTRAPPER_BINARY__URL\" = \"http$SECURE://rhche-$RH_CHE_PROJECT_NAMESPACE.devtools-dev.ext.devshift.net/agent-binaries/linux_amd64/bootstrapper/bootstrapper\" |
                      .\"data\".\"CHE_API\" = \"http$SECURE://rhche-$RH_CHE_PROJECT_NAMESPACE.devtools-dev.ext.devshift.net/api\" |
                      .\"data\".\"CHE_WEBSOCKET_ENDPOINT\" = \"ws$SECURE://rhche-$RH_CHE_PROJECT_NAMESPACE.devtools-dev.ext.devshift.net/api/websocket\" |
                      .\"data\".\"CHE_WEBSOCKET_ENDPOINT__MINOR\" = \"ws$SECURE://rhche-$RH_CHE_PROJECT_NAMESPACE.devtools-dev.ext.devshift.net/api/websocket-minor\" |
                      .\"metadata\".\"name\" = \"rhche\" |
                      .\"data\".\"CHE_INFRA_OPENSHIFT_TLS__ENABLED\" = \"$RH_CHE_USE_TLS\" ")

if [ "$RH_CHE_USE_CUSTOM_REPOSITORIES" == "true" ]; then
  CHE_CONFIG_YAML=$(echo "$CHE_CONFIG_YAML" | \
                    yq ".\"data\".\"CHE_WORKSPACE_PLUGIN__REGISTRY__URL\" = \"http://che-plugin-registry:8080/v3\" |
                        .\"data\".\"CHE_WORKSPACE_DEVFILE__REGISTRY__URL\" = \"http://che-devfile-registry:8080/\" ")
fi

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

if [ "${RH_CHE_USE_TLS}" != "true" ]; then
  CHE_APP_CONFIG_YAML=$(echo "$CHE_APP_CONFIG_YAML" | yq "del (.objects[] | select(.kind == \"Route\").spec.tls)")
fi

if ! (echo "$CHE_APP_CONFIG_YAML" | oc process -f - | oc apply -f - > /dev/null 2>&1); then
  echo -e "\\033[91;1mFailed to process che config [$?]\\033[0m"
  exit 5
fi

# APPLY SECRET IF REQUESTED
if [ "$RH_CHE_APPLY_SECRET" == "true" ]; then
  echo "$RH_CHE_OC_SECRET" | oc apply -f -
  oc secrets link default quay-dev-deployer --for=pull
  oc secrets link rhche quay-dev-deployer --for=pull
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
  echo -e "Getting events from deployment:"
  oc get events -n ${RH_CHE_PROJECT_NAMESPACE}
  exit 1
fi

if [ "${RH_CHE_USE_TLS}" == "true" ]; then
  echo -e "Annotating route"
  oc annotate --overwrite=true route/rhche kubernetes.io/tls-acme=true
else
  echo -e "Annotating route skipped"
fi

echo -e "\\033[92;1mSUCCESS: Rh-Che deployed on \\033[34mhttp$SECURE://rhche-$RH_CHE_PROJECT_NAMESPACE.devtools-dev.ext.devshift.net/\\033[0m"

# CLEANUP
if [ "$RH_CHE_DEPLOY_SCRIPT_CLEANUP" = true ]; then
  echo -e "\\033[1mCleaning up.\\033[0m"
  clearEnv
fi
unsetVars
echo -e "\\033[92;1mDone.\\033[0m"
