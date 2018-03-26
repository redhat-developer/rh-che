#!/bin/bash
# Copyright (c) 2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

usage="\033[93;1m$(basename "$0") \033[0;1m{-5} [-u <username>] [-p <passwd>] [-o <token>] \033[90m{-t <tag>} {-r <registry>} {-e <namespace>} {-b <rh-che branch>} {-n} {-s} {-h} \033[0m-- Login to dev cluster and deploy che with specific build tag

\033[32;1mwhere:\033[0m
    \033[1m-5\033[0m  \033[93muse che-server V5 \033[31;1m!!MUST COME AS FIRST FLAG!!\033[0m
    \033[1m-u\033[0m  \033[93musername for openshift account\033[0m
    \033[1m-p\033[0m  \033[93mpassword for openshift account\033[0m
    \033[1m-o\033[0m  \033[93mopenshift token - \033[31;1meither token or username and password must be provided\033[0m
    \033[1m-e\033[0m  use specified namespace instead of the default one
    \003[1m-b\033[0m  use specified rh-che github branch
    \033[1m-h\033[0m  show this help text
    \033[1m-n\033[0m  do not delete files after script finishes
    \033[1m-s\033[0m  wipe sql database (postgres)
    \033[1m-t\033[0m  [\033[1mdefault=latest\033[0m] tag for specific build (first 7 characters of commit hash)
    \033[1m-r\033[0m  docker image registry from where to pull

\033[32;1mrequirements\033[0m:
    \033[1moc\033[0m  openshift origin CLI admin (dnf install origin-clients)
    \033[1mjq\033[0m  json CLI processor (dnf install jq)
    \033[1myq\033[0m  yaml CLI processor based on jq (pip install yq)"

export RH_CHE_DEPLOY_SCRIPT_CLEANUP="true";
export RH_CHE_WIPE_SQL="false";
export RH_CHE_IS_V_FIVE="false";
export RH_CHE_OPENSHIFT_USE_TOKEN="false";
export RH_CHE_OPENSHIFT_URL=https://dev.rdu2c.fabric8.io:8443;
export RH_CHE_JDBC_USERNAME=pgche;
export RH_CHE_JDBC_PASSWORD=pgchepassword;
export RH_CHE_JDBC_URL=jdbc:postgresql://postgres:5432/dbche;

function setVars() {
  if [ "$RH_CHE_IS_V_FIVE" == "true" ]; then
    export RH_CHE_PROJECT_ID="c82f44b-fabric8-9cc154c";
    export RH_CHE_DOCKER_IMAGE_REGISTRY="rhche/che-server-multiuser";
    export RH_CHE_PROJECT_NAMESPACE=che5-automated;
    export RH_CHE_GITHUB_BRANCH=master;
  else
#    export RH_CHE_PROJECT_ID="nightly-fabric8";
#    export RH_CHE_DOCKER_IMAGE_REGISTRY="dfestal/che-server";
    export RH_CHE_PROJECT_ID="3e14772-fabric8-e228731";
    export RH_CHE_DOCKER_IMAGE_REGISTRY="rhche/rh-che-server";
    export RH_CHE_PROJECT_NAMESPACE=che6-automated;
    export RH_CHE_GITHUB_BRANCH=rh-che6;
  fi
}

function unsetVars() {
  unset RH_CHE_OPENSHIFT_USERNAME;
  unset RH_CHE_OPENSHIFT_PASSWORD;
  unset RH_CHE_OPENSHIFT_TOKEN;
  unset RH_CHE_PROJECT_ID;
  unset RH_CHE_OPENSHIFT_URL;
  unset RH_CHE_DOCKER_IMAGE_REGISTRY;
  unset RH_CHE_DEPLOY_SCRIPT_CLEANUP;
  unset RH_CHE_WIPE_SQL;
  unset RH_CHE_PROJECT_NAMESPACE;
  unset RH_CHE_GITHUB_BRANCH;
  unset RH_CHE_JDBC_USERNAME;
  unset RH_CHE_JDBC_PASSWORD;
  unset RH_CHE_JDBC_URL;
  unset RH_CHE_IS_V_FIVE;
  unset IMAGE_POSTGRES;
  unset RH_CHE_STATUS_PROGRESS;
  unset RH_CHE_STATUS_AVAILABLE;
}

function clearEnv() {
  rm wait_until_postgres_is_available.sh 2>1 > /dev/null
  rm deploy_postgres_only.sh 2>1 > /dev/null
  rm che-init-image-stream.yaml 2>1 > /dev/null
  rm rh-che.app.yaml 2>1 > /dev/null
  rm rh-che.config.yaml 2>1 > /dev/null
  rm -rf postgres 2>1 > /dev/null
}

function checkCheStatus() {
  if [ "$RH_CHE_IS_V_FIVE" == "true" ]; then
    RH_CHE_DEPLOYMENT_OC_STATUS=$(oc get dc che -o json)
  else
    RH_CHE_DEPLOYMENT_OC_STATUS=$(oc get dc rhche -o json)
  fi
  export RH_CHE_STATUS_PROGRESS=$(echo "$RH_CHE_DEPLOYMENT_OC_STATUS" | jq ".status.conditions | map(select(.type == \"Progressing\").status)[]")
  export RH_CHE_STATUS_AVAILABLE=$(echo "$RH_CHE_DEPLOYMENT_OC_STATUS" | jq ".status.conditions | map(select(.type == \"Available\").status)[]")
}

function deployPostgres() {
  echo -e "\033[1mDeploying PostgreSQL\033[0;38;5;60m"
  if ! (./deploy_postgres_only.sh); then
    echo -e "\033[0;91;1mFailed to deploy PostgreSQL\033[0m"
    exit 1
  fi
  echo -e "\033[0;92;1mPostreSQL database successfully deployed\033[0m"
}

# PREPARE VARIABLES FOR V6
setVars

while getopts ':5hnsu:p:r:t:o:e:b:' option; do
  case "$option" in
    5) # SET VARIABLES FOR V5 !!MUST COME AS FIRST FLAG!!
       export RH_CHE_IS_V_FIVE="true"
       setVars
       ;;
    h) echo -e "$usage"
       exit 0
       ;;
    n) export RH_CHE_DEPLOY_SCRIPT_CLEANUP="false"
       ;;
    s) export RH_CHE_WIPE_SQL="true"
       ;;
    t) export RH_CHE_PROJECT_ID=$OPTARG
       ;;
    u) export RH_CHE_OPENSHIFT_USERNAME=$OPTARG
       ;;
    p) export RH_CHE_OPENSHIFT_PASSWORD=$OPTARG
       ;;
    o) export RH_CHE_OPENSHIFT_TOKEN=$OPTARG
       export RH_CHE_OPENSHIFT_USE_TOKEN="true"
       ;;
    r) export RH_CHE_DOCKER_IMAGE_REGISTRY=$OPTARG
       ;;
    e) export RH_CHE_PROJECT_NAMESPACE=$OPTARG
       ;;
    b) export RH_CHE_GITHUB_BRANCH=$OPTARG
       ;;
    :) echo -e "\033[91;1mMissing argument for -$OPTARG\033[0m" >&2
       echo -e "$usage" >&2
       unsetVars
       exit 1
       ;;
   \?) echo -e "\033[91;1mIllegal option: -$OPTARG\033[0m" >&2
       echo -e "$usage" >&2
       unsetVars
       exit 1
       ;;
  esac
done
shift $((OPTIND - 1))

if [ "$RH_CHE_OPENSHIFT_USE_TOKEN" == "true" ]; then
  if [[ -z "$RH_CHE_OPENSHIFT_TOKEN" ]]; then
    echo -e "\033[91;1mOpenshift token not provided."
    unsetVars
    echo -e "$usage" >&2
    exit 1
  fi
else
  if [[ -z "$RH_CHE_OPENSHIFT_USERNAME" ]]; then
    echo -e "\033[91;1mOpenshift username not provided.\033[0m"
    unsetVars
    echo -e "$usage" >&2
    exit 1
  fi
  if [[ -z "$RH_CHE_OPENSHIFT_PASSWORD" ]]; then
    echo -e "\033[91;1mOpenshift password not provided.\033[0m"
    unsetVars
    echo -e "$usage" >&2
    exit 1
  fi
fi

# LOGIN TO OPENSHIFT CONSOLE
echo -e "\033[1mLogging in to openshift...\033[0m";
if [ "$RH_CHE_OPENSHIFT_USE_TOKEN" == "true" ]; then
  if ! (oc login $RH_CHE_OPENSHIFT_URL --insecure-skip-tls-verify=true --token="$RH_CHE_OPENSHIFT_TOKEN" 2>1 > /dev/null); then
    echo -e "\033[91;1mOpenshift login with token failed\033[0m"
    exit 1
  fi
else
  if ! (oc login $RH_CHE_OPENSHIFT_URL --insecure-skip-tls-verify=true -u "$RH_CHE_OPENSHIFT_USERNAME" -p "$RH_CHE_OPENSHIFT_PASSWORD" 2>1 > /dev/null); then
    echo -e "\033[91;1mOpenshift login failed\033[0m"
    exit 1
  fi
fi
echo -e "\033[92;1mLogin successful, creating project \033[34m$RH_CHE_PROJECT_NAMESPACE\033[0;38;5;238m";

# CREATE PROJECT
if [ "$RH_CHE_IS_V_FIVE" == "true" ]; then
  if ! (oc project $RH_CHE_PROJECT_NAMESPACE 2>1 > /dev/null); then
    oc new-project $RH_CHE_PROJECT_NAMESPACE --display-name='RH-Che5 Automated Deployment' 2>1 > /dev/null
  fi
else
  if ! (oc project $RH_CHE_PROJECT_NAMESPACE 2>1 > /dev/null); then
    oc new-project $RH_CHE_PROJECT_NAMESPACE --display-name='RH-Che6 Automated Deployment' 2>1 > /dev/null
  fi
fi

# GET DEPLOYMENT SCRIPTS
echo -e "\033[0;1mGetting deployment scripts...\033[0m"
WAIT_SCRIPT_URL=https://raw.githubusercontent.com/eclipse/che/master/deploy/openshift/multi-user/wait_until_postgres_is_available.sh
DEPLOY_POSTGRES_SCRIPT_URL=https://raw.githubusercontent.com/eclipse/che/master/deploy/openshift/multi-user/deploy_postgres_only.sh
DEPLOY_POSTGRES_CONFIG_URL=https://raw.githubusercontent.com/eclipse/che/master/deploy/openshift/multi-user/che-init-image-stream.yaml
curl -L0fs $WAIT_SCRIPT_URL -o wait_until_postgres_is_available.sh 2>1 > /dev/null
curl -L0fs $DEPLOY_POSTGRES_SCRIPT_URL -o deploy_postgres_only.sh 2>1 > /dev/null
curl -L0fs $DEPLOY_POSTGRES_CONFIG_URL -o che-init-image-stream.yaml 2>1 > /dev/null

# GET POSTGRES CONFIGS
mkdir postgres 2>1 > /dev/null
cd postgres || exit 1
export IMAGE_POSTGRES="eclipse/che-postgres"
if ! (curl -L0fs https://raw.githubusercontent.com/eclipse/che/master/deploy/openshift/multi-user/postgres/deployment-config.yaml -o deployment-config.yaml 2>1 > /dev/null); then
  echo -e "\033[93;1mFile postgres-data-claim.yaml is missing.\033[0m"
  rm deployment-config.yaml
fi
if ! (curl -L0fs https://raw.githubusercontent.com/eclipse/che/master/deploy/openshift/multi-user/postgres/postgres-data-claim.yaml -o postgres-data-claim.yaml 2>1 > /dev/null); then
  echo -e "\033[93;1mFile postgres-data-claim.yaml is missing.\033[0m"
  rm postgres-data-claim.yaml
fi
if ! (curl -L0fs https://raw.githubusercontent.com/eclipse/che/master/deploy/openshift/multi-user/postgres/service.yaml -o service.yaml 2>1 > /dev/null); then
  echo -e "\033[93;1mFile service.yaml is missing.\033[0m"
  rm service.yaml
fi
cd ../ || exit 1

# GET CHE-APP CONFIGS
if ! (curl -L0fs https://raw.githubusercontent.com/redhat-developer/rh-che/$RH_CHE_GITHUB_BRANCH/openshift/rh-che.app.yaml -o rh-che.app.yaml 2>1 > /dev/null); then
  echo -e "\033[91;1mCould not download che app definition config!\033[0m"
  exit 1
fi
if ! (curl -L0fs https://raw.githubusercontent.com/redhat-developer/rh-che/$RH_CHE_GITHUB_BRANCH/openshift/rh-che.config.yaml -o rh-che.config.yaml 2>1 > /dev/null); then
  echo -e "\033[91;1mCould not download che config yaml!\033[0m"
  exit 1
fi

chmod 777 wait_until_postgres_is_available.sh
chmod 777 deploy_postgres_only.sh
echo -e "\033[92;1mGetting deployment scripts done.\033[0m"

# DEPLOY POSTRES
if [ "$RH_CHE_WIPE_SQL" == "true" ]; then
  if (oc get dc postgres 2>1 > /dev/null); then
    oc delete dc postgres
  fi
  deployPostgres
else
  if ! (oc get dc postgres 2>1 > /dev/null); then
    deployPostgres
  fi
fi

# APPLY CHE CONFIGMAP
#yq '' ./rh-che.config.yaml | cat
CHE_CONFIG_YAML=$(yq ".\"data\".\"che-keycloak-auth-server-url\" = \"https://sso.prod-preview.openshift.io/auth\" | 
                      .\"data\".\"keycloak-github-endpoint\" = \"https://auth.prod-preview.openshift.io/api/token?for=https://github.com\" | 
                      .\"data\".\"keycloak-oso-endpoint\" = \"https://sso.prod-preview.openshift.io/auth/realms/fabric8/broker/openshift-v3/token\" | 
                      .\"data\".\"service.account.secret\" = \"\" | 
                      .\"data\".\"service.account.id\" = \"\" | 
                      .\"data\".\"che.jdbc.username\" = \"$RH_CHE_JDBC_USERNAME\" | 
                      .\"data\".\"che.jdbc.password\" = \"$RH_CHE_JDBC_PASSWORD\" | 
                      .\"data\".\"che.jdbc.url\" = \"$RH_CHE_JDBC_URL\" " ./rh-che.config.yaml)

if [ "$RH_CHE_IS_V_FIVE" == "true" ]; then
  CHE_CONFIG_YAML=$(echo "$CHE_CONFIG_YAML" | \
                    yq ".\"data\".\"che-workspace-che-server-endpoint\" = \"https://che-$RH_CHE_PROJECT_NAMESPACE.dev.rdu2c.fabric8.io/wsmaster/api\" | 
                        .\"data\".\"che-host\" = \"che-$RH_CHE_PROJECT_NAMESPACE.dev.rdu2c.fabric8.io\" | 
                        .\"data\".\"infra-bootstrapper-binary-url\" = \"https://che-$RH_CHE_PROJECT_NAMESPACE.dev.rdu2c.fabric8.io/agent-binaries/linux_amd64/bootstrapper/bootstrapper\" | 
                        .\"data\".\"che-api\" = \"https://che-$RH_CHE_PROJECT_NAMESPACE.dev.rdu2c.fabric8.io/api\" | 
                        .\"data\".\"che-websocket-endpoint\" = \"wss://che-$RH_CHE_PROJECT_NAMESPACE.dev.rdu2c.fabric8.io/api/websocket\" | 
                        .\"metadata\".\"name\" = \"che\" ")
else
  CHE_CONFIG_YAML=$(echo "$CHE_CONFIG_YAML" | \
                    yq ".\"data\".\"che-host\" = \"rhche-$RH_CHE_PROJECT_NAMESPACE.dev.rdu2c.fabric8.io\" | 
                        .\"data\".\"infra-bootstrapper-binary-url\" = \"https://rhche-$RH_CHE_PROJECT_NAMESPACE.dev.rdu2c.fabric8.io/agent-binaries/linux_amd64/bootstrapper/bootstrapper\" | 
                        .\"data\".\"che-api\" = \"https://rhche-$RH_CHE_PROJECT_NAMESPACE.dev.rdu2c.fabric8.io/api\" | 
                        .\"data\".\"che-websocket-endpoint\" = \"wss://rhche-$RH_CHE_PROJECT_NAMESPACE.dev.rdu2c.fabric8.io/api/websocket\" | 
                        .\"metadata\".\"name\" = \"rhche\" ")
fi

#echo "$CHE_CONFIG_YAML"
if ! (echo "$CHE_CONFIG_YAML" | oc apply -f - 2>1 > /dev/null); then
  echo -e "\033[91;1mFailed to apply configmap.\033[0m"
  exit 1
fi
echo -e "\033[92;1mChe config deployed on \033[34m${RH_CHE_PROJECT_ID}\033[0m"

# PROCESS CHE APP CONFIG
CHE_APP_CONFIG_YAML=$(yq "" ./rh-che.app.yaml)
CHE_APP_CONFIG_YAML=$(echo "$CHE_APP_CONFIG_YAML" | \
                      yq "(.parameters[] | select(.name == \"IMAGE\").value) |= \"$RH_CHE_DOCKER_IMAGE_REGISTRY\" | 
                          (.parameters[] | select(.name == \"IMAGE_TAG\").value) |= \"$RH_CHE_PROJECT_ID\" | 
                          (.objects[] | select(.kind == \"DeploymentConfig\").spec.template.spec.containers[0].imagePullPolicy) |= \"Always\"")
if [ "$RH_CHE_IS_V_FIVE" == "true" ]; then
  CHE_APP_CONFIG_YAML=$(echo "$CHE_APP_CONFIG_YAML" | \
                        yq "(.objects[] | select(.kind == \"DeploymentConfig\").spec.template.spec.containers[0].env[] | 
                             select(.name == \"CHE_OPENSHIFT_SERVICE__ACCOUNT_SECRET\").valueFrom) |= {\"configMapKeyRef\":{\"key\":\"service.account.secret\",\"name\":\"che\"}} | 
                            (.objects[] | select(.kind == \"DeploymentConfig\").spec.template.spec.containers[0].env[] | 
                             select(.name == \"CHE_OPENSHIFT_SERVICE__ACCOUNT_ID\").valueFrom) |= {\"configMapKeyRef\":{\"key\":\"service.account.id\",\"name\":\"che\"}} | 
                            (.objects[] | select(.kind == \"DeploymentConfig\").spec.template.spec.containers[0].env[] | 
                             select(.name == \"CHE_JDBC_PASSWORD\").valueFrom) |= {\"configMapKeyRef\":{\"key\":\"che.jdbc.password\",\"name\":\"che\"}} | 
                            (.objects[] | select(.kind == \"DeploymentConfig\").spec.template.spec.containers[0].env[] | 
                             select(.name == \"CHE_JDBC_URL\").valueFrom) |= {\"configMapKeyRef\":{\"key\":\"che.jdbc.url\",\"name\":\"che\"}} | 
                            (.objects[] | select(.kind == \"DeploymentConfig\").spec.template.spec.containers[0].env[] | 
                             select(.name == \"CHE_JDBC_USERNAME\").valueFrom) |= {\"configMapKeyRef\":{\"key\":\"che.jdbc.username\",\"name\":\"che\"}}")
else
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
fi

#echo "$CHE_APP_CONFIG_YAML" | oc process -f - | cat
if ! (echo "$CHE_APP_CONFIG_YAML" | oc process -f - | oc apply -f - 2>1 > /dev/null); then
  echo -e "\033[91;1mFailed to process che config\033[0m"
  exit 1
fi

CHE_STARTUP_TIMEOUT=120
while [[ "${RH_CHE_STATUS_PROGRESS}" != "\"True\"" || "${RH_CHE_STATUS_AVAILABLE}" != "\"True\"" ]] && [ ${CHE_STARTUP_TIMEOUT} -gt 0 ]; do
  sleep 1
  checkCheStatus
  echo -e "\033[0;38;5;60mChe-server status: Available:\033[0;1m$RH_CHE_STATUS_AVAILABLE \033[0;38;5;60mProgressing:\033[0;1m$RH_CHE_STATUS_PROGRESS \033[0;38;5;60mTimeout:\033[0;1m$CHE_STARTUP_TIMEOUT\033[0m"
  CHE_STARTUP_TIMEOUT=$((CHE_STARTUP_TIMEOUT-1))
done
if [ $CHE_STARTUP_TIMEOUT == 0 ]; then
  echo -e "\033[91;1mFailed to start che-server: timed out\033[0m"
  exit 1
fi

if [ "$RH_CHE_IS_V_FIVE" == "true" ]; then
  oc annotate --overwrite=true route/che kubernetes.io/tls-acme=true
  echo -e "\033[92;1mSUCCESS: Che deployed on \033[34mhttps://che-$RH_CHE_PROJECT_NAMESPACE.dev.rdu2c.fabric8.io/\033[0m"
else
  oc annotate --overwrite=true route/rhche kubernetes.io/tls-acme=true
  echo -e "\033[92;1mSUCCESS: Rh-Che deployed on \033[34mhttps://rhche-$RH_CHE_PROJECT_NAMESPACE.dev.rdu2c.fabric8.io/\033[0m"
fi

# CLEANUP
if [ "$RH_CHE_DEPLOY_SCRIPT_CLEANUP" = true ]; then
  echo -e "\033[1mCleaning up.\033[0m"
  clearEnv
fi
unsetVars
echo -e "\033[92;1mDone.\033[0m"
