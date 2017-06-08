#!/bin/bash
set -u
set +e

echo ""
echo "#####################################"
echo "     STARTING CHE BUILD VALIDATION"
echo "#####################################"
echo ""

# Source build variables
cat jenkins-env | grep PASS > inherit-env
. inherit-env
. config 
. ~/che_image_tag.env

echo "CHE VALIDATION: Validating image ${DOCKER_HUB_NAMESPACE}/che-server:${CHE_SERVER_DOCKER_IMAGE_TAG} =="

# Install oc client
yum install -y centos-release-openshift-origin
yum install -y origin-clients

OSO_USER=mloriedo
OSO_TOKEN=$(curl -sSL -H "X-Vault-Token: ${DOCKER_HUB_PASSWORD}" -H "Content-Type: application/json" \
	-X GET http://li546-232.members.linode.com:8200/v1/secret/os_token | cut -d\" -f18)
if [[ -z "$OSO_TOKEN" ]]; then
    echo "CHE VALIDATION: Error OSO token is empty, cannot proceed with verification"
    exit 1
fi
echo "CHE VALIDATION: Retrieved OSO token for user ${OSO_USER}"

OSIO_VERSION=$(curl -sSL http://central.maven.org/maven2/io/fabric8/online/apps/che/maven-metadata.xml | grep latest | sed -e 's,.*<latest>\([^<]*\)</latest>.*,\1,g')
echo "CHE VALIDATION: Retrieved latest OSIO version to use for deployment: ${OSIO_VERSION}"

OSO_DOMAIN=8a09.starter-us-east-2.openshiftapps.com
OSO_API_ENDPOINT=https://api.starter-us-east-2.openshift.com
CHE_OPENSHIFT_HOSTNAME=${OSO_USER}-che.${OSO_DOMAIN}
CHE_OPENSHIFT_PROJECT=${OSO_USER}-che

oc login ${OSO_API_ENDPOINT} --token=${OSO_TOKEN}
oc project ${CHE_OPENSHIFT_PROJECT}

echo "CHE VALIDATION: Get next deployment config revision num"
CURRENT_DC_REVISION=$(oc get dc/che -o=custom-columns=NAME:.status.latestVersion --no-headers)
NEXT_DC_REVISION=$((CURRENT_DC_REVISION+1))
echo "CHE VALIDATION: Next revision number is ${NEXT_DC_REVISION}"

# Escape slashes in DOCKER_HUB_NAMESPACE to use it with sed later
# e.g. docker.io/rhchestage => docker.io\/rhchestage
DOCKER_HUB_NAMESPACE_SANITIZED=${DOCKER_HUB_NAMESPACE/\//\\\/}

echo "CHE VALIDATION: Deploying che-server"
curl -sSL http://central.maven.org/maven2/io/fabric8/online/apps/che/${OSIO_VERSION}/che-${OSIO_VERSION}-openshift.yml | \
    sed "s/    hostname-http:.*/    hostname-http: ${CHE_OPENSHIFT_HOSTNAME}/" | \
    sed "s/          image:.*/          image: ${DOCKER_HUB_NAMESPACE_SANITIZED}\/che-server:${CHE_SERVER_DOCKER_IMAGE_TAG}/" | \
oc apply -f - --force=true
echo "CHE VALIDATION: Applied http://central.maven.org/maven2/io/fabric8/online/apps/che/${OSIO_VERSION}/che-${OSIO_VERSION}-openshift.yml to ${OSO_API_ENDPOINT} project ${CHE_OPENSHIFT_PROJECT}"

## Check status of deployment
che_server_status=$(oc get pods | grep che-${NEXT_DC_REVISION} | grep -v che-${NEXT_DC_REVISION}-deploy | awk '{print $3}')
counter=0
timeout=240
echo "CHE VALIDATION: Checking state of Che pod"
# Wait up to 1 minutes for running Che pod
while [ "${che_server_status}" != "Running" ]; do
    che_server_status=$(oc get pods | grep che-${NEXT_DC_REVISION} | grep -v che-${NEXT_DC_REVISION}-deploy | awk '{print $3}')
    counter=$((counter+1))
    if [ $counter -gt $timeout ]; then
        echo "CHE VALIDATION: Che server is not running after ${timeout} seconds"
        exit 1
    fi
    sleep 1
done
echo "CHE VALIDATION: Che pod is running!"
server_log=$(oc logs dc/che)
counter=0
timeout=180
echo "CHE VALIDATION: Checking whether a Che server in Che pod has already started."
# Wait up to 1 minute for running Che server in pod 
while [[ $(echo "${server_log}" | grep "Server startup in" | wc -l) -ne 1 ]]; do
    server_log=$(oc logs dc/che)
    counter=$((counter+1))
    if [ $counter -gt $timeout ]; then
        echo "CHE VALIDATION: Error, server log does not contain information about server startup. Timeouted after ${timeout} seconds."
        exit 1
    fi
    sleep 1
done
echo "CHE VALIDATION: Che server is running!"

echo "CHE VALIDATION: Verification passed. Pushing Che server image to prod registry."
docker login -u ${DOCKER_HUB_USER} -p ${DOCKER_HUB_PASSWORD} -e noreply@redhat.com
docker tag ${DOCKER_HUB_NAMESPACE}/che-server:${CHE_SERVER_DOCKER_IMAGE_TAG} rhche/che-server:${CHE_SERVER_DOCKER_IMAGE_TAG}
docker push rhche/che-server:${CHE_SERVER_DOCKER_IMAGE_TAG}
echo "CHE VALIDATION: Image pushed"