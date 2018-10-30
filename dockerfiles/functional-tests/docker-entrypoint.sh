#!/bin/bash

if [[ -z "${RHCHE_ACC_USERNAME}" || -z "${RHCHE_ACC_PASSWORD}" || -z "${RHCHE_ACC_EMAIL}" || -z "${RHCHE_ACC_TOKEN}" ]]; then
  echo "Test credentials not set."
  echo "Usage:"
  echo -e "\tdocker run --name functional-tests-dep --privileged -v /var/run/docker.sock:/var/run/docker.sock \\"
  echo -e "\t       -e \"RHCHE_ACC_USERNAME=<username>\" \\"
  echo -e "\t       -e \"RHCHE_ACC_PASSWORD=<password>\" \\"
  echo -e "\t       -e \"RHCHE_ACC_EMAIL=<email>\" \\"
  echo -e "\t       -e \"RHCHE_ACC_TOKEN=<offline_token>\""
  echo "Optional parameters:"
  echo -e "\t       -v <local_logs_directory>:/home/fabric8/logs # Allows logs and screenshots to be collected"
  echo -e "\t       -v <local_functional-tests_full_path>:/home/fabric8/che/ # Allows mounting custom rh-che/functional-tests sources"
  echo -e "\t       # Run tests against custom deployment:"
  echo -e "\t       -e \"RHCHE_HOST_PROTOCOL=<http/https>\" # Protocol to be used, either http or https"
  echo -e "\t       -e \"RHCHE_HOST_URL=che.openshift.io\" # Which host to run tests against. Just use host name"
  echo -e "\t       -e \"RHCHE_OFFLINE_ACCESS_EXCHANGE=https://auth.<target>/api/token/refresh\" # Exchange url for refresh token"
  echo -e "\t       -e \"RHCHE_GITHUB_EXCHANGE=https://auth.<target>/api/token?for=https://github.com\" # Github API token exchange"
  echo -e "\t       -e \"RHCHE_OPENSHIFT_TOKEN_URL=https://sso.<target>/auth/realms/fabric8/broker\" # Openshift token exchange url"
  exit 0
fi

echo "Starting chromedriver"
nohup chromedriver &
echo "Running Xvfb"
nohup /usr/bin/Xvfb :99 -screen 0 1920x1080x24 +extension RANDR > /dev/null 2>&1 &
echo "Preparing environment"

export CHE_INFRASTRUCTURE=openshift
export CHE_MULTIUSER=true
export RHCHE_SCREENSHOTS_DIR=${RHCHE_SCREENSHOTS_DIR:-"/home/fabric8/rh-che/functional-tests/target/screenshots"}
export RHCHE_OFFLINE_ACCESS_EXCHANGE=${RHCHE_OFFLINE_ACCESS_EXCHANGE:-"https://auth.openshift.io/api/token/refresh"}
export RHCHE_GITHUB_EXCHANGE=${RHCHE_GITHUB_EXCHANGE:-"https://auth.openshift.io/api/token?for=https://github.com"}
export RHCHE_OPENSHIFT_TOKEN_URL=${RHCHE_OPENSHIFT_TOKEN_URL:-"https://sso.openshift.io/auth/realms/fabric8/broker/openshift-v3/token"}
export RHCHE_HOST_URL=${RHCHE_HOST_URL:-"che.openshift.io"}
export RHCHE_HOST_PROTOCOL=${RHCHE_HOST_PROTOCOL:-"https"}
export RHCHE_HOST_FULL_URL="${RHCHE_HOST_PROTOCOL}://${RHCHE_HOST_URL}/"
export RHCHE_EXCLUDED_GROUPS=${RHCHE_EXCLUDED_GROUPS:-"github"}

export SELF_CONTAINER_ID=$(cat /etc/hostname)

docker network create --attachable -d bridge localnetwork
docker network connect localnetwork "${SELF_CONTAINER_ID}"

if [ -d "/root/che" ]; then
  echo "Running functional-tests mounted to /root/che/"
  cd /root/che/
  else
  echo "Running functional-tests from embedded sources."
  cd /root/rh-che/
fi

echo "Running che-starter against ${RHCHE_HOST_FULL_URL}"

docker run -d -p 10000:10000 --name che-starter --network localnetwork \
  -e "GITHUB_TOKEN_URL=${RHCHE_GITHUB_EXCHANGE}" \
  -e "OPENSHIFT_TOKEN_URL=${RHCHE_OPENSHIFT_TOKEN_URL}" \
  -e "CHE_SERVER_URL=${RHCHE_HOST_FULL_URL}" \
  quay.io/openshiftio/almighty-che-starter:latest

echo "Running tests"

scl enable rh-maven33 rh-nodejs8 "mvn clean --projects functional-tests -Pfunctional-tests -B \
  -Dche.testuser.name=${RHCHE_ACC_USERNAME} \
  -Dche.testuser.email=${RHCHE_ACC_EMAIL} \
  -Dche.testuser.offline_token=${RHCHE_ACC_TOKEN} \
  -Dche.testuser.password=${RHCHE_ACC_PASSWORD} \
  -Dche.host=${RHCHE_HOST_URL} \
  -Dche.offline.to.access.token.exchange.endpoint=${RHCHE_OFFLINE_ACCESS_EXCHANGE} \
  -DexcludedGroups=${RHCHE_EXCLUDED_GROUPS} \
  -DcheStarterUrl=http://che-starter.localnetwork:10000 \
  -Dtests.screenshots_dir=${RHCHE_SCREENSHOTS_DIR} \
  test install"

if [ -d "/root/logs/" ]; then
  echo "Logs folder mounted, grabbing logs."
  echo "Saving che-starter logs to /root/logs/che-starter.log"
  docker logs che-starter > /root/logs/che-starter.log
  echo "Saving test run logs to /root/logs/functional-tests.log"
  docker logs "${SELF_CONTAINER_ID}" > /root/logs/functional-tests.log
  echo "Archiving artifacts to /root/logs/artifacts/"
  cp -r ./functional-tests/target/ /root/logs/artifacts/
fi

echo "Stopping che-starter"
docker container kill che-starter
docker container rm che-starter
docker network rm localnetwork
