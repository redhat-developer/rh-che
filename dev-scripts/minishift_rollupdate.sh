#!/bin/bash
set -e

commandDir=$(dirname "$0")
source ${commandDir}/../config
source ${commandDir}/env-for-minishift

oc rollout latest che -n ${CHE_OPENSHIFT_PROJECT}
