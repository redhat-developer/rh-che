#!/bin/bash

currentDir=`pwd`

. config 

RH_CHE_TAG=$(git rev-parse --short HEAD)

UPSTREAM_TAG=$(sed -n 's/^revision = \(.\{7\}\).*/\1/p' ${currentDir}/builds/fabric8-che/assembly/assembly-build-info/target/dependency/WEB-INF/classes/org/eclipse/che/ide/ext/help/client/BuildInfo.properties)

# Now lets build the local docker images
cd ${currentDir}/dockerfiles/che-fabric8

distPath='assembly/assembly-main/target/eclipse-che-*.tar.gz'
for distribution in `ls -1 ${currentDir}/builds/fabric8-che/${distPath};`
do
  case "$distribution" in
    ${currentDir}/builds/fabric8-che/assembly/assembly-main/target/eclipse-che-${RH_DIST_SUFFIX}-*${RH_NO_DASHBOARD_SUFFIX}*)
      TAG=${UPSTREAM_TAG}-${RH_DIST_SUFFIX}-no-dashboard-${RH_CHE_TAG}
      NIGHTLY=nightly-${RH_DIST_SUFFIX}-no-dashboard
      ;;
    ${currentDir}/builds/fabric8-che/assembly/assembly-main/target/eclipse-che-${RH_DIST_SUFFIX}*)
      TAG=${UPSTREAM_TAG}-${RH_DIST_SUFFIX}-${RH_CHE_TAG}
      NIGHTLY=nightly-${RH_DIST_SUFFIX}
      # File che_image_tag.env will be used by the verification script to
      # retrieve the image tag to promote to production. That's the only
      # mechanism we have found to share the tag amongs the two scripts
      echo 'export CHE_SERVER_DOCKER_IMAGE_TAG='${TAG} >> ~/che_image_tag.env
      ;;
  esac

  # fetch the right upstream based che-server image to build from
  docker pull ${CHE_DOCKER_BASE_IMAGE}
  docker tag ${CHE_DOCKER_BASE_IMAGE} eclipse/che-server:local

  DIR=$(cd "$(dirname "$0")"; pwd)

  # Use of folder
  LOCAL_ASSEMBLY_ZIP="${DIR}"/eclipse-che.tar.gz

  if [ -f "${LOCAL_ASSEMBLY_ZIP}" ]; then
    rm "${LOCAL_ASSEMBLY_ZIP}"
  fi

  echo "Linking assembly ${distribution} --> ${LOCAL_ASSEMBLY_ZIP}"
  ln "${distribution}" "${LOCAL_ASSEMBLY_ZIP}"



  bash ./build.sh --organization:${DOCKER_HUB_NAMESPACE} --tag:${NIGHTLY}
  if [ $? -ne 0 ]; then
    echo 'Docker Build Failed'
    exit 2
  fi

  # cleanup
  docker rmi eclipse/che-server:local

  # lets change the tag and push it to the registry
  docker tag ${DOCKER_HUB_NAMESPACE}/che-server:${NIGHTLY} ${DOCKER_HUB_NAMESPACE}/che-server:${TAG}
  
  dockerTags="${dockerTags} ${DOCKER_HUB_NAMESPACE}/che-server:${NIGHTLY} ${DOCKER_HUB_NAMESPACE}/che-server:${TAG}"
    
  if [ "$DeveloperBuild" != "true" ]
  then
    docker login -u ${DOCKER_HUB_USER} -p $DOCKER_HUB_PASSWORD -e noreply@redhat.com 
    
    docker push ${DOCKER_HUB_NAMESPACE}/che-server:${NIGHTLY}
    docker push ${DOCKER_HUB_NAMESPACE}/che-server:${TAG}
    
    if [ "${DOCKER_HUB_USER}" == "${RHCHEBOT_DOCKER_HUB_USER}" ]; then
      # lets also push it to push.registry.devshift.net
      if ([ -z "${DEVSHIFT_USERNAME+x}" ] || [ -z "${DEVSHIFT_PASSWORD+x}" ]); then
        echo "WARNING: Cannot push to registry.devshift.net: credentials are not set"
      else
        docker login -u ${DEVSHIFT_USERNAME} -p ${DEVSHIFT_PASSWORD} -e noreply@redhat.com push.registry.devshift.net
        docker tag ${DOCKER_HUB_NAMESPACE}/che-server:${NIGHTLY} push.registry.devshift.net/che/che:${NIGHTLY}
        docker tag ${DOCKER_HUB_NAMESPACE}/che-server:${NIGHTLY} push.registry.devshift.net/che/che:${TAG}
        docker push push.registry.devshift.net/che/che:${NIGHTLY}
        docker push push.registry.devshift.net/che/che:${TAG}
      fi
    fi
  fi
done

if [ "${DOCKER_HOST}" == "" ]; then
  dockerEnv="local Docker environment"
else
  dockerEnv="following Docker environment: ${DOCKER_HOST}"
fi

echo "!"
echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
echo "!"
echo "! Created / tagged the following eclipse Che images:"
echo "!     ${dockerTags}"
echo "! in the ${dockerEnv}"
echo "!"
echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
echo "!"

