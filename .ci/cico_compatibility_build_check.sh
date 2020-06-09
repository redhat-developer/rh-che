#!/bin/bash

yum -y update
yum -y install centos-release-scl-rh scl-utils java-1.8.0-openjdk-devel git 
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
PREV_CHE_VERSION=$(scl enable rh-maven33 'mvn help:evaluate -Dexpression=che.version -q -DforceStdout')
echo ">>> current Che version: $PREV_CHE_VERSION"
echo ">>> change upstream version to: $CHE_VERSION"
scl enable rh-maven33 'mvn versions:update-parent  versions:commit -DallowSnapshots=true -DparentVersion=[$CHE_VERSION]'
cat pom.xml | sed -e "s%<che.version>$PREV_CHE_VERSION</che.version>%<che.version>$CHE_VERSION</che.version>%" > pom.xml.updated
rm pom.xml
mv pom.xml.updated pom.xml
scl enable rh-maven33 'mvn clean install'
