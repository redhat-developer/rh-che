#!/bin/bash
set -u
set +e

# Source build variables
cat jenkins-env | grep -e ^CHE_ > inherit-env
. inherit-env
. config 
. ~/che_image_tag.env

# Install oc client
yum install -y centos-release-openshift-origin
yum install -y origin-clients

OSO_TOKEN=$(curl -sSL -H "X-Vault-Token: ${RHCHEBOT_DOCKER_HUB_PASSWORD}" -H "Content-Type: application/json" \
	-X GET http://li546-232.members.linode.com:8200/v1/secret/os_token | cut -d\" -f18)
OSO_USER=mloriedo
OSO_DOMAIN=8a09.starter-us-east-2.openshiftapps.com
OSO_API_ENDPOINT=https://api.starter-us-east-2.openshift.com
OSIO_VERSION=$(curl -sSL http://central.maven.org/maven2/io/fabric8/online/apps/che/maven-metadata.xml | grep latest | sed -e 's,.*<latest>\([^<]*\)</latest>.*,\1,g')
CHE_OPENSHIFT_HOSTNAME=${OSO_USER}-che.${OSO_DOMAIN}
CHE_OPENSHIFT_PROJECT=${OSO_USER}-che
oc login ${OSO_API_ENDPOINT} --token=${OSO_TOKEN}
oc project ${CHE_OPENSHIFT_PROJECT}
echo "Getting version of OSIO and applying template"
curl -sSL http://central.maven.org/maven2/io/fabric8/online/apps/che/${OSIO_VERSION}/che-${OSIO_VERSION}-openshift.yml | \
    sed "s/    hostname-http:.*/    hostname-http: ${CHE_OPENSHIFT_HOSTNAME}/" | \
    sed "s/          image:.*/          image: ${DOCKER_HUB_NAMESPACE}\/che-server:${CHE_SERVER_DOCKER_IMAGE_TAG}/" | \
oc apply -f -
## Check status of deployment
che_server_status=$(oc get pods | awk '{ if ($1 ~ /che-[0-9]+-.*/ && $1 !~ /che-[0-9]+-deploy/) print $3 }')
counter=0
timeout=60
echo "Checking state of Che pod."
# Wait up to 1 minutes for running Che pod
while [ "${che_server_status}" != "Running" ]; do
    che_server_status=$(oc get pods | awk '{ if ($1 ~ /che-[0-9]+-.*/ && $1 !~ /che-[0-9]+-deploy/) print $3 }')
    counter=$((counter+1))
    if [ $counter -gt $timeout ]; then
        echo "Che server is not running after ${timeout} seconds"
        exit 1
    fi
    sleep 1
done
echo "Che pod is running."
server_log=$(oc logs dc/che)
counter=0
timeout=60
echo "Checking whether a Che server in Che pod has already started."
# Wait up to 1 minute for running Che server in pod 
while [[ $(echo "${server_log}" | grep ".*Server startup in [0-9]\+ ms.*" | wc -l) -ne 1 ]]; do
    server_log=$(oc logs dc/che)
    counter=$((counter+1))
    if [ $counter -gt $timeout ]; then
        echo "Server log does not contain information about server startup. Timeouted after ${timeout} seconds."
        exit 1
    fi
    sleep 1
done
echo "Che server is running."

echo "Verification passed. Pushing Che server image to prod registry."
docker login -u ${DOCKER_HUB_USER} -p $DOCKER_HUB_PASSWORD -e noreply@redhat.com
docker tag ${DOCKER_HUB_NAMESPACE}/che-server:${CHE_SERVER_DOCKER_IMAGE_TAG} rhche/che-server:${CHE_SERVER_DOCKER_IMAGE_TAG}
docker push rhche/che-server:${CHE_SERVER_DOCKER_IMAGE_TAG}
