#!/bin/bash

set -e

DEFAULT_CHE_IMAGE=codenvy/che-server:local

if [ -z ${GITHUB_REPO+x} ]; then 
  echo >&2 "Variable GITHUB_REPO not found. Aborting"
  exit 1
fi

CHE_IMAGE=${CHE_IMAGE:-${DEFAULT_CHE_IMAGE}}

CURRENT_DIR=$(pwd)
cd ${GITHUB_REPO}

mvnche() {
  mvn -Dskip-enforce -Dskip-validate-sources -DskipTests -Dfindbugs.skip -Dgwt.compiler.localWorkers=2 -T 1C -Dskip-validate-sources $@
}

cd plugins/plugin-docker
mvnche clean install
cd ../..

cd wsmaster/che-core-api-agent
mvnche clean install
cd ../che-core-api-workspace
mvnche clean install
cd ../..

cd assembly/assembly-wsmaster-war
mvnche clean install
cd ../..

cd assembly/assembly-main/
mvnche clean install
cd ../..

docker build -t ${CHE_IMAGE} .
cd ${CURRENT_DIR}