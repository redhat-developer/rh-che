#!/bin/bash

set -e

DEFAULT_CHE_IMAGE_TAG=nightly

if [ -z ${GITHUB_REPO+x} ]; then 
  echo >&2 "Variable GITHUB_REPO not found. Aborting"
  exit 1
fi

CHE_IMAGE_TAG=${CHE_IMAGE_TAG:-${DEFAULT_CHE_IMAGE_TAG}}

CURRENT_DIR=$(pwd)
cd ${GITHUB_REPO}

mvnche() {
  mvn -Dskip-enforce -Dskip-validate-sources -DskipTests -Dfindbugs.skip -Dgwt.compiler.localWorkers=2 -T 1C -Dskip-validate-sources $@
}

cd plugins/plugin-docker
mvnche install
cd ../..

cd assembly/assembly-wsmaster-war
mvnche install
cd ../..

cd assembly/assembly-main/
mvnche install
cd ../..

cd dockerfiles/che/
./build.sh ${CHE_IMAGE_TAG}

cd ${CURRENT_DIR}

