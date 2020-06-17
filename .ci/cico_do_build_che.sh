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

mvnche() {
  mvn -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn $*
}

cd ${scriptDir}/../
mkdir $NPM_CONFIG_PREFIX 2>/dev/null
mvnche -B $* clean install
if [ $? -ne 0 ]; then
  echo "Error building che/rh-che"
  exit 1;
fi