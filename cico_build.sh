#!/bin/bash
# Just a script to get and build eclipse-che locally
# please send PRs to github.com/kbsingh/build-run-che

# update machine, get required deps in place
# this script assumes its being run on CentOS Linux 7/x86_64

cat jenkins-env | grep PASS > inherit-env
. inherit-env
. config 

yum -y update
yum -y install centos-release-scl java-1.8.0-openjdk-devel git patch bzip2 golang docker subversion
yum -y install rh-maven33 rh-nodejs4

useradd ${BuildUser}
groupadd docker
gpasswd -a ${BuildUser} docker

systemctl start docker

chown ${BuildUser}:${BuildUser} .

currentDir=`pwd`
buildCommand="cd ${currentDir} && ./build_che.sh"
runuser - ${BuildUser} -c "$buildCommand"
if [ $? -eq 0 ]; then

  RH_CHE_TAG=$(git rev-parse --short HEAD)
  
  cd target/export/che-dependencies/che
  UPSTREAM_TAG=$(git rev-parse --short HEAD)

  # Now lets build the local docker images
  cd dockerfiles/che/
  cat Dockerfile.centos > Dockerfile

  distPath='assembly/assembly-main/target/eclipse-che-*.tar.gz'
  for distribution in `ls -1 ${currentDir}/target/export/che-dependencies/che/${distPath}; ls -1 ${currentDir}/target/builds/fabric8*/fabric8-che/${distPath};`
  do
    case "$distribution" in
      ${currentDir}/target/builds/fabric8-${RH_NO_DASHBOARD_SUFFIX}/fabric8-che/assembly/assembly-main/target/eclipse-che-*-${RH_DIST_SUFFIX}-${RH_NO_DASHBOARD_SUFFIX}*)
        # We are not pushing the the no-dashboard tag because CI has an issue
        # when publishing > 1 tag at a time 
        # TAG=${UPSTREAM_TAG}-${RH_DIST_SUFFIX}-no-dashboard-${RH_CHE_TAG}
        # NIGHTLY=nightly-${RH_DIST_SUFFIX}-no-dashboard
        continue
        ;;
      ${currentDir}/target/builds/fabric8/fabric8-che/assembly/assembly-main/target/eclipse-che-*-${RH_DIST_SUFFIX}*)
        TAG=${UPSTREAM_TAG}-${RH_DIST_SUFFIX}-${RH_CHE_TAG}
        NIGHTLY=nightly-${RH_DIST_SUFFIX}
        ;;
      ${currentDir}/target/export/che-dependencies/che/assembly/assembly-main/target/eclipse-che-*)
        # We are not pushing the the upstream tag because CI has an issue
        # when publishing > 1 tag at a time 
        # TAG=${UPSTREAM_TAG}
        # NIGHTLY=nightly
        continue
        ;;
    esac
        
    rm ../../assembly/assembly-main/target/eclipse-che-*.tar.gz
    cp ${distribution} ../../assembly/assembly-main/target

    bash ./build.sh
    if [ $? -ne 0 ]; then
      echo 'Docker Build Failed'
      exit 2
    fi
    
    # lets change the tag and push it to the registry
    docker tag eclipse/che-server:nightly ${DOCKER_HUB_NAMESPACE}/che-server:${NIGHTLY}
    docker tag eclipse/che-server:nightly ${DOCKER_HUB_NAMESPACE}/che-server:${TAG}
    docker login -u ${DOCKER_HUB_USER} -p $DOCKER_HUB_PASSWORD -e noreply@redhat.com
    # We are not pushing the nightly tag because we don't need it and CI has an issue
    # when publishing > 1 tag at a time 
    # docker push ${DOCKER_HUB_NAMESPACE}/che-server:${NIGHTLY}
    docker push ${DOCKER_HUB_NAMESPACE}/che-server:${TAG}
    
    if [ "${DOCKER_HUB_USER}" == "${RHCHEBOT_DOCKER_HUB_USER}" ]; then
    # lets also push it to registry.devshift.net
      docker tag ${DOCKER_HUB_NAMESPACE}/che-server:${NIGHTLY} registry.devshift.net/che/che:${NIGHTLY}
      docker tag ${DOCKER_HUB_NAMESPACE}/che-server:${NIGHTLY} registry.devshift.net/che/che:${TAG}
      # We are not pushing the nightly tag because we don't need it and CI has an issue
      # when publishing > 1 tag at a time 
      #docker push registry.devshift.net/che/che:${NIGHTLY}
      docker push registry.devshift.net/che/che:${TAG}
    fi
  done
    
else
  echo 'Build Failed!'
  exit 1
fi
