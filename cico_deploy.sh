#!/bin/bash
set -u
set +e

# Prepare config files
# CHE_SERVER_DOCKER_IMAGE_TAG has to be set in che_image_tag.env file
. config
. ~/che_image_tag.env
config_file=~/tests_config
echo "export OSO_MASTER_URL=https://api.starter-us-east-2.openshift.com:443" >> $config_file
echo "export OSO_NAMESPACE=mlabuda-jenkins" >> $config_file
echo "export OSO_USER=mlabuda" >> $config_file
echo "export OSO_DOMAIN=8a09.starter-us-east-2.openshiftapps.com" >> $config_file
echo "export OSO_HOSTNAME=mlabuda-jenkins.8a09.starter-us-east-2.openshiftapps.com" >> $config_file
echo "export CHE_SERVER_DOCKER_IMAGE_TAG=$CHE_SERVER_DOCKER_IMAGE_TAG" >> $config_file
echo "export DOCKER_HUB_NAMESPACE=${DOCKER_HUB_NAMESPACE}" >> $config_file

# Triggers update of tenant and execution of functional tests
git clone git@github.com:redhat-developer/che-functional-tests.git
mv $config_file che-functional-tests/config
cp jenkins-env che-functional-tests/jenkins-env
cd che-functional-tests
. cico_run_EE_tests.sh

echo "CHE VALIDATION: Verification passed. Pushing Che server image to prod registry."
docker login -u ${DOCKER_HUB_USER} -p ${DOCKER_HUB_PASSWORD} -e noreply@redhat.com
docker tag ${DOCKER_HUB_NAMESPACE}/che-server:${CHE_SERVER_DOCKER_IMAGE_TAG} rhche/che-server:${CHE_SERVER_DOCKER_IMAGE_TAG}
docker push rhche/che-server:${CHE_SERVER_DOCKER_IMAGE_TAG}
echo "CHE VALIDATION: Image pushed"
