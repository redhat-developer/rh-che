#!/bin/bash

if [[ -z "${RHCHE_ACC_USERNAME}" || -z "${RHCHE_ACC_PASSWORD}" || -z "${RHCHE_ACC_EMAIL}" ]]; then
  echo "Test credentials not set."
  echo "Usage:"
  echo -e "\tdocker run --name functional-tests-dep --privileged -v /var/run/docker.sock:/var/run/docker.sock \\"
  echo -e "\t       -e \"RHCHE_ACC_USERNAME=<username>\" \\"
  echo -e "\t       -e \"RHCHE_ACC_PASSWORD=<password>\" \\"
  echo -e "\t       -e \"RHCHE_ACC_EMAIL=<email>\" \\"
  echo "Optional parameters:"
  echo -e "\t       -v <local_logs_directory>:/root/logs # Allows logs and screenshots to be collected"
  echo -e "\t       -v <local_functional-tests_full_path>:/root/che/ # Allows mounting custom rh-che/functional-tests sources"
  echo -e "\t       # Run tests against custom deployment:"
  echo -e "\t       -e \"RHCHE_HOST_PROTOCOL=<http/https>\" # Protocol to be used, either http or https"
  echo -e "\t       -e \"RHCHE_HOST_URL=che.openshift.io\" # Which host to run tests against. Just use host name"
  echo -e "\t       -e \"CHE_OSIO_AUTH_ENDPOINT=<endpoint>\" # endpoint for auth e.g. https://auth.prod-preview.openshift.io "
  echo -e "\t       -e \"RHCHE_GITHUB_EXCHANGE=https://auth.<target>/api/token?for=https://github.com\" # Github API token exchange"
  echo -e "\t       -e \"RHCHE_OPENSHIFT_TOKEN_URL=https://sso.<target>/auth/realms/fabric8/broker\" # Openshift token exchange url"
  echo -e "\t       -e \"TEST_SUITE=<xml> # Name of xml file with testing suite"
  echo -e "\t		-e \"RUNNING_WORKSPACE=<name> # Name of running workspace to be used in test"
  echo -e "\t       -e \"OPENSHIFT_URL=<url> # Url of openshift - used only in tests where manipulation with OpenShift environment is needed"
  echo -e "\t       -e \"OPENSHIFT_TOKEN=<token> # Token for login to OpenShift - used only in tests where manipulation with OpenShift environment is needed"
  exit 1
fi

echo "Starting chromedriver"
nohup chromedriver &
echo "Running Xvfb"
nohup /usr/bin/Xvfb :99 -screen 0 1920x1080x24 +extension RANDR > /dev/null 2>&1 &
echo "Preparing environment"

export CHE_INFRASTRUCTURE=OSIO
export CHE_MULTIUSER=true
export RHCHE_SCREENSHOTS_DIR=${RHCHE_SCREENSHOTS_DIR:-"/home/fabric8/rh-che/functional-tests/target/screenshots"}
export CHE_OSIO_AUTH_ENDPOINT=${CHE_OSIO_AUTH_ENDPOINT:-"https://auth.openshift.io"}
export RHCHE_OFFLINE_ACCESS_EXCHANGE=${RHCHE_OFFLINE_ACCESS_EXCHANGE:-"${CHE_OSIO_AUTH_ENDPOINT}/api/token/refresh"}
export RHCHE_GITHUB_EXCHANGE=${RHCHE_GITHUB_EXCHANGE:-"${CHE_OSIO_AUTH_ENDPOINT}/api/token?for=https://github.com"}
export RHCHE_OPENSHIFT_TOKEN_URL=${RHCHE_OPENSHIFT_TOKEN_URL:-"https://sso.openshift.io/auth/realms/fabric8/broker/openshift-v3/token"}
export RHCHE_HOST_URL=${RHCHE_HOST_URL:-"che.openshift.io"}
export RHCHE_HOST_PROTOCOL=${RHCHE_HOST_PROTOCOL:-"https"}
export RHCHE_HOST_FULL_URL="${RHCHE_HOST_PROTOCOL}://${RHCHE_HOST_URL}/"
export RHCHE_EXCLUDED_GROUPS=${RHCHE_EXCLUDED_GROUPS:-"github"}
export TEST_SUITE=${TEST_SUITE:-"simpleTestSuite.xml"}

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

MVN_COMMAND="mvn clean --projects functional-tests -Pfunctional-tests -B \
  -Dche.testuser.name=${RHCHE_ACC_USERNAME} \
  -Dche.testuser.email=${RHCHE_ACC_EMAIL} \
  -Dche.testuser.password=${RHCHE_ACC_PASSWORD} \
  -Dche.host=${RHCHE_HOST_URL} \
  -Dche.osio.auth.endpoint=${CHE_OSIO_AUTH_ENDPOINT} \
  -DexcludedGroups=${RHCHE_EXCLUDED_GROUPS} \
  -DcheStarterUrl=http://che-starter.localnetwork:10000 \
  -Dtests.screenshots_dir=${RHCHE_SCREENSHOTS_DIR} \
  -Dtest.suite=${TEST_SUITE}"

if [[ -n $RUNNING_WORKSPACE ]]; then
	MVN_COMMAND="${MVN_COMMAND} -Dche.workspaceName=${RUNNING_WORKSPACE}"
fi

if [[ "$TEST_SUITE" == "rolloutTest.xml" ]]; then
	export OPENSHIFT_URL=$OPENSHIFT_URL
	export OPENSHIFT_TOKEN=$OPENSHIFT_TOKEN
		
	MVN_COMMAND="${MVN_COMMAND} \
	  -Dche.admin.name=${RHCHE_ACC_USERNAME} \
  	  -Dche.admin.email=${RHCHE_ACC_EMAIL} \
	  -Dche.admin.password=${RHCHE_ACC_PASSWORD} \
	  -Dche.protocol=${RHCHE_HOST_PROTOCOL} \
	  -Dche.port=80 \
	  -Dche.openshift.project=${OPENSHIFT_PROJECT}"
fi

scl enable rh-maven33 rh-nodejs8 "$MVN_COMMAND test install"
RETURN_CODE=$?

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

exit $RETURN_CODE
