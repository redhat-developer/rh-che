#!/bin/bash

COMMAND_DIR=$(dirname "$0")
bash ${COMMAND_DIR}/build_openshift_connector.sh
bash ${COMMAND_DIR}/build_fabric8_and_deploy.sh -DlocalCheRepository="${UPSTREAM_CHE_PATH}" -Dpl='assembly/assembly-wsmaster-war' -Damd $*

