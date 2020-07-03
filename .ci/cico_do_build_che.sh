#!/bin/bash
# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

# this script downloads runs the build
# to create the che binaries

scriptDir=$(dirname "$0")

source ${scriptDir}/../config

installDependencies() {
  yum -y update &&  yum -y install java-11-openjdk-devel git
  mkdir -p /opt/apache-maven && curl -sSL https://downloads.apache.org/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz | tar -xz --strip=1 -C /opt/apache-maven
  export JAVA_HOME=/usr/lib/jvm/java-11-openjdk
  export PATH="/usr/lib/jvm/java-11-openjdk:/opt/apache-maven/bin:/usr/bin:${PATH:-/bin:/usr/bin}"
  export JAVACONFDIRS="/etc/java${JAVACONFDIRS:+:}${JAVACONFDIRS:-}"
  export M2_HOME="/opt/apache-maven"
}

mvnche() {
  mvn -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn $*
}

cd ${scriptDir}/../
mkdir $NPM_CONFIG_PREFIX 2>/dev/null
installDependencies
mvnche -B $* clean install
if [ $? -ne 0 ]; then
  echo "Error building che/rh-che"
  exit 1;
fi
