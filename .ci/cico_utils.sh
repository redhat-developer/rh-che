#!/usr/bin/env bash

# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

function installOC() {
  OC_VERSION=3.10.90
  curl -s "https://mirror.openshift.com/pub/openshift-v3/clients/${OC_VERSION}/linux/oc.tar.gz" | tar xvz -C /usr/local/bin
}

function installJQ() {
  installEpelRelease
  yum install --assumeyes -d1 jq
}

function installJava() {
  yum install --assumeyes -d1 java-11-openjdk-devel
}

function installEpelRelease() {
  if yum repolist | grep epel; then
    echo "Epel already installed, skipping instalation."
  else
    #excluding mirror1.ci.centos.org
    echo "exclude=mirror1.ci.centos.org" >> /etc/yum/pluginconf.d/fastestmirror.conf
    echo "Installing epel..."
    yum install -d1 --assumeyes epel-release
    yum update --assumeyes -d1
  fi
}

function installYQ() {
  installEpelRelease
  yum install --assumeyes -d1 python3-pip
  pip3 install --upgrade setuptools
  pip3 install yq
}

function installDocker() {
  yum install --assumeyes -d1 yum-utils device-mapper-persistent-data lvm2
  yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
  yum install --assumeyes -d1 docker-ce
  systemctl start docker
  docker version
}

function installScl(){
  if yum repolist | grep centos-release-scl; then
    echo "SCL already installed, skipping instalation."
  else
    echo "SCL not installed, installing..."
    yum install --assumeyes -d1 centos-release-scl
    yum install --assumeyes -d1 scl-utils
  fi
}

function installMvn() {
  installScl
  mkdir -p /opt/apache-maven && curl -sSL https://downloads.apache.org/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz | tar -xz --strip=1 -C /opt/apache-maven
  export PATH="/opt/apache-maven/bin:${PATH:-/bin:/usr/bin}"
  export M2_HOME="/opt/apache-maven"
}

function installNodejs() {
  yum install --assumeyes -d1 rh-nodejs8
}

function installGit(){
  yum install --assumeyes -d1 git
}

function installDependencies() {
  installEpelRelease
  installYQ
  installDocker
  installJQ
  installOC
  installGit
  installScl
  installJava
  # Getting dependencies ready
  yum install --assumeyes -d1 \
              patch \
              pcp \
              bzip2 \
              golang \
              make
  installMvn
  installNodejs
}

function installDependenciesForCompatibilityCheck() {
  installEpelRelease
  installYQ
  installJQ
  installGit
  installJava
  installMvn
}

function checkAllCreds() {
  CREDS_NOT_SET="false"

  if [[ -z "${QUAY_USERNAME}" || -z "${QUAY_PASSWORD}" ]]; then
    echo "Docker registry credentials not set"
    CREDS_NOT_SET="true"
  fi

  if [[ -z "${RH_CHE_AUTOMATION_DEV_CLUSTER_SA_TOKEN}" ]]; then
    echo "RDU2C credentials not set"
    CREDS_NOT_SET="true"
  fi

  if [[ -z "${RH_CHE_AUTOMATION_CHE_PREVIEW_EMAIL}" ]] ||
     [[ -z "${RH_CHE_AUTOMATION_CHE_PREVIEW_USERNAME}" ]] ||
     [[ -z "${RH_CHE_AUTOMATION_CHE_PREVIEW_PASSWORD}" ]]; then
    echo "Prod-preview credentials not set."
    CREDS_NOT_SET="true"
  fi

  if [[ "${CREDS_NOT_SET}" = "true" ]]; then
    echo "Failed to parse jenkins secure store credentials"
    exit 2
  else
    echo "Credentials set successfully."
  fi
}

function archiveArtifacts() {
  set +e
  echo "Archiving artifacts from ${DATE} for ${JOB_NAME}/${BUILD_NUMBER}"
  ls -la ./artifacts.key
  chmod 600 ./artifacts.key
  chown $(whoami) ./artifacts.key
  mkdir -p ./rhche/${JOB_NAME}/${BUILD_NUMBER}/surefire-reports
  cp ./logs/*.log ./rhche/${JOB_NAME}/${BUILD_NUMBER}/ | true
  cp -R ./logs/artifacts/screenshots/ ./rhche/${JOB_NAME}/${BUILD_NUMBER}/ | true
  cp -R ./logs/artifacts/failsafe-reports/ ./rhche/${JOB_NAME}/${BUILD_NUMBER}/ | true
  cp ./events_report.txt ./rhche/${JOB_NAME}/${BUILD_NUMBER}/ | true
  rsync --password-file=./artifacts.key -Hva --partial --relative ./rhche/${JOB_NAME}/${BUILD_NUMBER} devtools@artifacts.ci.centos.org::devtools/
  set -e
}

function getVersionFromPom() {
  version=$(mvn -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn org.apache.maven.plugins:maven-help-plugin:evaluate -q -Dexpression=project.parent.version -DforceStdout)
  echo $version
}

function getMavenVersion() {
  version=$(mvn -v)
  echo $version
}

setURLs() {
  if [ -z $ACCOUNT_ENV ]; then
    #ACCOUNT_ENV variable is not set, retrieving env from username.
    #verify environment - if production or prod-preview
    rm -rf cookie-file loginfile.html
    if [[ "$USERNAME" == *"preview"* ]] || [[ "$USERNAME" == *"saas"* ]]; then
      export USER_INFO_URL="https://api.prod-preview.openshift.io/api/users?filter[username]=$USERNAME"
      export LOGIN_PAGE_URL="https://auth.prod-preview.openshift.io/api/login?redirect=https://che.openshift.io"
    else
      export USER_INFO_URL="https://api.openshift.io/api/users?filter[username]=$USERNAME"
      export LOGIN_PAGE_URL="https://auth.openshift.io/api/login?redirect=https://che.openshift.io"
    fi
  else
    if [ "$ACCOUNT_ENV" == "prod" ]; then
      export USER_INFO_URL="https://api.openshift.io/api/users?filter[username]=$USERNAME"
      export LOGIN_PAGE_URL="https://auth.openshift.io/api/login?redirect=https://che.openshift.io"
    else
      export USER_INFO_URL="https://api.prod-preview.openshift.io/api/users?filter[username]=$USERNAME"
      export LOGIN_PAGE_URL="https://auth.prod-preview.openshift.io/api/login?redirect=https://che.openshift.io"
    fi
  fi
}

function getActiveToken() {
  rm -rf cookie-file loginfile.html
  setURLs

  response=$(curl -s -g -X GET --header 'Accept: application/json' $USER_INFO_URL)
  data=$(echo "$response" | jq .data)
  if [ "$data" == "[]" ]; then
    exit 1
  fi

  #get html of developers login page
  curl -sX GET -L -c cookie-file -b cookie-file $LOGIN_PAGE_URL > loginfile.html

  #get url for login from form
  url=$(grep "form id" loginfile.html | grep -o 'http.*.tab_id=.[^\"]*')
  dataUrl="username=$USERNAME&password=$PASSWORD&login=Log+in"
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
    echo ${token}
  else
    exit 1
  fi
}

function getVersionFromProdPreview() {
  token=$(getActiveToken)
  version=$(curl -s -X OPTIONS --header "Content-Type: application/json" --header "Authorization: Bearer ${token}" https://che.prod-preview.openshift.io/api/ | jq '.buildInfo')
  version=${version//\"/}
  echo $version
}

function getVersionFromProd() {
  token=$(getActiveToken)
  version=$(curl -s -X OPTIONS --header "Content-Type: application/json" --header "Authorization: Bearer ${token}" https://che.openshift.io/api/ | jq '.buildInfo')
  version=${version//\"/}
  echo $version
}
