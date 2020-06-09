# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

FROM library/centos:centos7

ENV LANG=en_US.utf8 \
    DISPLAY=:99 \
    FABRIC8_USER_NAME=fabric8

COPY google-chrome.repo /etc/yum.repos.d/google-chrome.repo
RUN groupadd docker -g 1000 && \
    yum update --assumeyes && \
    yum install --assumeyes epel-release && \
    yum install --assumeyes google-chrome-stable && \
    yum install --assumeyes \
        xorg-x11-server-Xvfb \
        java-1.8.0-openjdk \
        java-1.8.0-openjdk-devel \
        git \
        unzip \
        docker \
        centos-release-scl \
        scl-utils && \
    yum install --assumeyes \
        rh-maven33 \
        rh-nodejs8 && \
    yum clean all --assumeyes && \
    rm -rf /var/cache/yum && \ 
    # Install oc and jq as a part of debuggin issue https://github.com/redhat-developer/che-functional-tests/issues/476
    package=opensfhit-origin-client.tar.gz && \
    curl -L -o /tmp/$package https://github.com/openshift/origin/releases/download/v3.11.0/openshift-origin-client-tools-v3.11.0-0cbc58b-linux-64bit.tar.gz && \
    tar --strip 1 -xzf /tmp/$package -C /tmp && \
    yum install jq --assumeyes && \
    # Get compatible versions of chrome and chromedriver
    chrome_version=$(google-chrome --version | grep -oiE "[0-9]*\.[0-9]*\.[0-9]*") && \
    chromedriver_version=$(curl -s https://chromedriver.storage.googleapis.com/LATEST_RELEASE_${chrome_version}) && \
    $(curl -sS https://chromedriver.storage.googleapis.com/${chromedriver_version}/chromedriver_linux64.zip > chromedriver_linux64.zip) && \
    unzip chromedriver_linux64.zip && mv chromedriver /usr/bin/chromedriver && chmod +x /usr/bin/chromedriver && rm chromedriver_linux64.zip && \
    # Install all dependencies needed for a test
    git clone https://github.com/redhat-developer/rh-che.git /root/rh-che && \
    scl enable rh-nodejs8 rh-maven33 "mvn clean install -B -f /root/rh-che/ --projects functional-tests -Pnative -DskipTests=true"

WORKDIR /root/
COPY docker-entrypoint.sh /root/
ENTRYPOINT ["/root/docker-entrypoint.sh"]
