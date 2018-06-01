#!/bin/bash
# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

export DeveloperBuild="true"

currentDir=$(pwd)

cd "$(dirname "$0")"/.. || (echo "Failed to cd $(dirname "$0")"; exit 1)

if ! bash .ci/cico_do_docker_build_tag_push.sh; then
  echo 'Build Failed!'
  cd "${currentDir}" || (echo "Failed to cd ${currentDir}"; exit 1)
  exit 1
fi
cd "${currentDir}" || (echo "Failed to cd ${currentDir}"; exit 1)
