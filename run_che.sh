#!/bin/bash
# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

# if it builds, lets run it locally

DataDir=~/.che/
mkdir -p ${DataDir}/lib
mkdir -p ${DataDir}/workspaces
mkdir -p ${DataDir}/storage

docker run -d --name che \
       -p 8080:8080 \
       -e CHE_DOCKER_SERVER__EVALUATION__STRATEGY=docker-local \
       -v /var/run/docker.sock:/var/run/docker.sock \
       -v ${DataDir}:/data:Z  \
       --security-opt label:disable \
       rhche/che-server:nightly

echo -n "Che is available at http://"
docker port che 8080/tcp
