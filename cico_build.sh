#!/bin/bash
# Just a script to get and build eclipse-che locally
# please send PRs to github.com/kbsingh/build-run-che

# update machine, get required deps in place
# this script assumes its being run on CentOS Linux 7/x86_64

currentDir=`pwd`

if [ "$DeveloperBuild" != "true" ]
then
  set +x
  cat jenkins-env | grep -e PASS -e DEVSHIFT > inherit-env
  . inherit-env
  set -x
  yum -y update
  yum -y install centos-release-scl java-1.8.0-openjdk-devel git patch bzip2 golang docker subversion
  yum -y install rh-maven33 rh-nodejs4
  
  BuildUser="chebuilder"

  useradd ${BuildUser}
  groupadd docker
  gpasswd -a ${BuildUser} docker
  
  systemctl start docker
  
  chmod a+x ..
  chown -R ${BuildUser}:${BuildUser} ${currentDir}
  
  runBuild() {
    runuser - ${BuildUser} -c "$*"
  }
else
  runBuild() {
    eval $*
  }
fi

. config 

runBuild "cd ${currentDir} && bash ./cico_do_build_che.sh $*"
if [ $? -eq 0 ]; then
  bash cico_do_docker_build_tag_push.sh
else
  echo 'Build Failed!'
  exit 1
fi
