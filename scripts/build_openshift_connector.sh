#!/bin/bash

set -e

DEFAULT_CHE_IMAGE_REPO=rhche/che-server
DEFAULT_CHE_IMAGE_TAG=nightly

if [ -z ${GITHUB_REPO+x} ]; then 
  echo >&2 "Variable GITHUB_REPO not found. Aborting"
  exit 1
fi

CHE_IMAGE_REPO=${CHE_IMAGE_REPO:-${DEFAULT_CHE_IMAGE_REPO}}
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
mv Dockerfile Dockerfile.alpine
cp Dockerfile.centos Dockerfile
./build.sh ${CHE_IMAGE_TAG}
mv Dockerfile.alpine Dockerfile
docker tag eclipse/che-server:${CHE_IMAGE_TAG} ${CHE_IMAGE_REPO}:${CHE_IMAGE_TAG}

cd ${CURRENT_DIR}

