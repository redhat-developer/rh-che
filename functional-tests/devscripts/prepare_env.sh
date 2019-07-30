#!/usr/bin/env bash
echo "***** Installing dependencies for functional tests $(date) ******"

start=$(date +%s)
set -e
echo "Installing dependencies..."

source .ci/functional_tests_utils.sh
installJQ
installStartDocker
installOC

export HOST_URL=$HOST_URL
eval "$(./env-toolkit load -f jenkins-env.json -r  USERNAME PASSWORD EMAIL OFFLINE_TOKEN JOB_NAME BUILD_NUMBER)"

mkdir logs

end=$(date +%s)
instal_deps_duration=$(($end - $start))
echo "Installing dependencies lasted $instal_deps_duration seconds."
