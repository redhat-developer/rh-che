#!/bin/bash

yum -y update
yum -y install centos-release-scl-rh java-1.8.0-openjdk-devel git 
yum -y install rh-maven33
yum install -y yum-utils device-mapper-persistent-data lvm2
yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
curl -sL https://rpm.nodesource.com/setup_10.x | bash -
yum-config-manager --add-repo https://dl.yarnpkg.com/rpm/yarn.repo
yum install -y docker-ce nodejs yarn gcc-c++ make
service docker start
set -e

git rebase origin/master || (echo "ERROR: Rebasing branch 'upstream' on top of 'master' failed"; exit 1)

CHE_VERSION=$(curl -s https://raw.githubusercontent.com/eclipse/che/master/pom.xml | grep "^    <version>.*</version>$" | awk -F'[><]' '{print $3}')
echo ">>> change upstream version to: $CHE_VERSION"
scl enable rh-maven33 'mvn versions:update-parent  versions:commit -DallowSnapshots=true -DparentVersion=[$CHE_VERSION] -Dmaven.repo.local=${WORKSPACE}/.repository -U'
scl enable rh-maven33 'mvn versions:update-property -Dproperty=che.version -DnewVersion=[$CHE_VERSION] -DallowDowngrade=true -DgenerateBackupPoms=false -Dmaven.repo.local=${WORKSPACE}/.repository'
scl enable rh-maven33 'mvn clean install -Dmaven.repo.local=${WORKSPACE}/.repository'
