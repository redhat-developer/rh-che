#!/bin/bash

COMMAND_DIR=$(dirname "$0")

export nextBuildStep="build_fabric8_and_rollupdate.sh"
bash ${COMMAND_DIR}/build_openshift_connector.sh $*
