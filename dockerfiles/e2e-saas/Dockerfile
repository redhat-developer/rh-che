# Copyright (c) 2019 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

ARG TAG=latest
FROM quay.io/eclipse/che-e2e:$TAG

ENV LANG=en_US.utf8 \
    FABRIC8_USER_NAME=fabric8

# currently in tmp/e2e
RUN npm run tsc

# install git
RUN apt-get update &&\
    apt-get install -y git

# Download rh-che
RUN cd ../ && \
    git clone https://github.com/redhat-developer/rh-che.git && \
    cd rh-che/e2e-saas && \
    npm --silent i

WORKDIR /tmp/
COPY docker-entrypoint.sh /tmp/

ENTRYPOINT ["/tmp/docker-entrypoint.sh"]
