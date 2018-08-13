# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

FROM library/centos:centos7

USER root

ENV LANG=en_US.utf8 \
    DISPLAY=:99 \
    FABRIC8_USER_NAME=fabric8
ENV HOME=/home/$FABRIC8_USER_NAME
ENV WORKSPACE=$HOME/che

COPY google-chrome.repo /etc/yum.repos.d/google-chrome.repo

RUN yum update --assumeyes && \
    yum install --assumeyes \
        xorg-x11-server-Xvfb \
        git \
        maven \
        google-crome-stable && \
    yum clean all --assumeyes && \
    rm -rf /var/cache/yum && \
    useradd --user-group --create-home --shell /bin/false ${FABRIC8_USER_NAME} && \
    chown -R ${FABRIC8_USER_NAME}:${FABRIC8_USER_NAME} $HOME && \
    rm -rf /home/fabric8/.pki

USER ${FABRIC8_USER_NAME}
WORKDIR $WORKSPACE/

RUN mkdir $HOME/tmp/ && cd $HOME/tmp/ && git clone https://github.com/redhat-developer/che-functional-tests.git && \
    cd $HOME/tmp/che-functional-tests && mvn clean install -DskipTests=true && \
    cd $HOME/ && rm -rf tmp

COPY docker-entrypoint.sh $HOME/

ENTRYPOINT ["/home/fabric8/docker-entrypoint.sh"]
