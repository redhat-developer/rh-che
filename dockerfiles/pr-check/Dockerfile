# Copyright (c) 2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

FROM library/centos:centos7

USER root

RUN yum update --assumeyes && \
    yum install --assumeyes tree git patch pcp bzip2 golang make java-1.8.0-openjdk java-1.8.0-openjdk-devel centos-release-scl && \
    yum install --assumeyes rh-maven33 rh-nodejs4 && yum clean all && rm -rf /var/cache/yum && \
    scl enable rh-nodejs4 "npm install --global gulp-cli" && \
    cd /tmp/ && git clone https://github.com/redhat-developer/rh-che.git && \
    scl enable rh-maven33 rh-nodejs4 "mvn -f /tmp/rh-che/ clean install" && rm -rf /tmp/rh-che/
