#!/bin/bash

currentDir=`pwd`

. config 

source target/upstreamCheRepository.env
RH_CHE_TAG=$(git rev-parse --short HEAD)

cd ${upstreamCheRepository}
upstreamCheRepoFullPath=`pwd`
UPSTREAM_TAG=$(git rev-parse --short HEAD)

# Now lets build the local docker images
mkdir ${currentDir}/target/docker 2>/dev/null
cp -R dockerfiles ${currentDir}/target/docker

cd ${currentDir}/target/docker/dockerfiles/che
cat Dockerfile.centos > Dockerfile

distPath='assembly/assembly-main/target/eclipse-che-*.tar.gz'
for distribution in `ls -1 ${upstreamCheRepoFullPath}/${distPath}; ls -1 ${currentDir}/target/builds/fabric8*/fabric8-che/${distPath};`
do
  case "$distribution" in
    ${currentDir}/target/builds/fabric8-${RH_NO_DASHBOARD_SUFFIX}/fabric8-che/assembly/assembly-main/target/eclipse-che-*-${RH_DIST_SUFFIX}-${RH_NO_DASHBOARD_SUFFIX}*)
      TAG=${UPSTREAM_TAG}-${RH_DIST_SUFFIX}-no-dashboard-${RH_CHE_TAG}
      NIGHTLY=nightly-${RH_DIST_SUFFIX}-no-dashboard
      ;;
    ${currentDir}/target/builds/fabric8/fabric8-che/assembly/assembly-main/target/eclipse-che-*-${RH_DIST_SUFFIX}*)
      TAG=${UPSTREAM_TAG}-${RH_DIST_SUFFIX}-${RH_CHE_TAG}
      NIGHTLY=nightly-${RH_DIST_SUFFIX}
      # File che_image_tag.env will be used by the verification script to
      # retrieve the image tag to promote to production. That's the only
      # mechanism we have found to share the tag amongs the two scripts
      echo 'export CHE_SERVER_DOCKER_IMAGE_TAG='${TAG} >> ~/che_image_tag.env
      ;;
    ${upstreamCheRepoFullPath}/assembly/assembly-main/target/eclipse-che-*)
      TAG=${UPSTREAM_TAG}
      NIGHTLY=nightly
      ;;
  esac
      
  rm ../../assembly/assembly-main/target/eclipse-che-*.tar.gz
  mkdir -p ../../assembly/assembly-main/target
  cp ${distribution} ../../assembly/assembly-main/target

  bash ./build.sh
  if [ $? -ne 0 ]; then
    echo 'Docker Build Failed'
    exit 2
  fi
  
  # lets change the tag and push it to the registry
  docker tag eclipse/che-server:nightly ${DOCKER_HUB_NAMESPACE}/che-server:${NIGHTLY}
  docker tag eclipse/che-server:nightly ${DOCKER_HUB_NAMESPACE}/che-server:${TAG}
  
  dockerTags="${dockerTags} ${DOCKER_HUB_NAMESPACE}/che-server:${NIGHTLY}"
    
  if [ "$DeveloperBuild" != "true" ]
  then
    docker login -u ${DOCKER_HUB_USER} -p $DOCKER_HUB_PASSWORD -e noreply@redhat.com 
    
    docker push ${DOCKER_HUB_NAMESPACE}/che-server:${NIGHTLY}
    docker push ${DOCKER_HUB_NAMESPACE}/che-server:${TAG}
    
    if [ "${DOCKER_HUB_USER}" == "${RHCHEBOT_DOCKER_HUB_USER}" ]; then
    # lets also push it to push.registry.devshift.net
      docker login -u $DEVSHIFT_USERNAME -p $DEVSHIFT_PASSWORD push.registry.devshift.net
      docker tag ${DOCKER_HUB_NAMESPACE}/che-server:${NIGHTLY} push.registry.devshift.net/che/che:${NIGHTLY}
      docker tag ${DOCKER_HUB_NAMESPACE}/che-server:${NIGHTLY} push.registry.devshift.net/che/che:${TAG}
      docker push push.registry.devshift.net/che/che:${NIGHTLY}
      docker push push.registry.devshift.net/che/che:${TAG}
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

