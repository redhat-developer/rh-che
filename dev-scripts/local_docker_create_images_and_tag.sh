#!/bin/bash
# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

export DeveloperBuild="true"

currentDir=$(pwd)

cd $(dirname "$0")/../.ci/

bash ./cico_do_docker_build_tag_push.sh
if [ $? -ne 0 ]; then
  echo 'Build Failed!'
  cd ${currentDir}
  exit 1
fi
cd ${currentDir}
