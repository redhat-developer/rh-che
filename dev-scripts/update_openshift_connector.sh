#!/bin/bash

set -e

if [ -z ${UPSTREAM_CHE_PATH+x} ]; then 
  echo >&2 "Variable UPSTREAM_CHE_PATH not found. Aborting"
  exit 1
fi

echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
echo "!"
echo "! Using Upstream Che repo from LOCAL directory : "
echo "!     ${UPSTREAM_CHE_PATH} "
echo "!"
echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"

# Build openshift-connector branch of che-dependencies
rm -rf ./che-deps
git clone -b openshift-connector --single-branch https://github.com/eclipse/che-dependencies.git che-deps
cd che-deps
mvn clean install
cd ..
rm -rf ./che-deps

COMMAND_DIR=$(dirname "$0")
CURRENT_DIR=$(pwd)
cd ${UPSTREAM_CHE_PATH}

mvnche() {
  mvn -Dskip-enforce -Dskip-validate-sources -DskipTests -Dfindbugs.skip -Dgwt.compiler.localWorkers=2 -T 1C $@
}

if [[ "$@" =~ "clean" ]]; then
  cleanArg="clean"
else
  cleanArg=""
fi

ModulesToBuild="\
plugins/plugin-docker/che-plugin-docker-compose,\
plugins/plugin-docker/che-plugin-docker-machine,\
plugins/plugin-docker/che-plugin-openshift-client,\
assembly/assembly-wsmaster-war,\
assembly/assembly-main"

mvnche -pl "$ModulesToBuild" $cleanArg install

cd ${CURRENT_DIR}
set -x
nextBuildStep=${nextBuildStep:-"build_fabric8.sh"}

bash ${COMMAND_DIR}/${nextBuildStep} -DlocalCheRepository="${UPSTREAM_CHE_PATH}" -Dpl='assembly/assembly-wsmaster-war' -Damd $*
