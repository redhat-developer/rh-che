# Copyright (c) 2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

FROM library/centos:centos7

EXPOSE 8089

RUN yum update --assumeyes \
 && yum install --assumeyes \
    git \
    gcc \
    make \
    openssl-devel \
    bzip2-devel \
    libffi-devel

WORKDIR /usr/src/
RUN curl https://www.python.org/ftp/python/3.7.2/Python-3.7.2.tgz --output Python-3.7.2.tgz && tar xzf Python-3.7.2.tgz \
 && cd Python-3.7.2 && ./configure --enable-optimizations && make altinstall && make clean \
 && python3.7 -m pip install locustio locust websocket-client python-dateutil

WORKDIR /
RUN git clone --single-branch --branch=master https://github.com/redhat-developer/rh-che.git rh-che-loadtesting
WORKDIR /rh-che-loadtesting/load-tests
COPY entrypoint.sh entrypoint.sh
ENTRYPOINT ["/rh-che-loadtesting/load-tests/entrypoint.sh"]
