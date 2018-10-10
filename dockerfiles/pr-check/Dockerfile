# Copyright (c) 2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

FROM library/centos:centos7

# Install dependencies; note rh-nodejs8 is required for dashboard build to succeed
RUN yum update --assumeyes \
 && yum install --assumeyes \
        docker \
        tree \
        git \
        patch \
        pcp \
        bzip2 \
        golang \
        make \
        java-1.8.0-openjdk \
        java-1.8.0-openjdk-devel \
        centos-release-scl \
 && yum install --assumeyes \
        rh-maven33 \
        rh-nodejs8 \
 && yum clean all \
 && rm -rf /var/cache/yum

# Bower update step won't work for root user
RUN useradd -ms /bin/bash newuser
USER newuser

# Clone rhche and build
RUN cd /tmp/ && git clone https://github.com/redhat-developer/rh-che.git \
 && scl enable rh-maven33 rh-nodejs8 "mvn -B -f /tmp/rh-che/ -Pnative clean install" && rm -rf /tmp/rh-che/

