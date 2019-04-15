# Copyright (c) 2012-2018 Red Hat, Inc
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

# This is a Dockerfile allowing to build dashboard by using a docker container.
# Build step: $ docker build -t eclipse-che-dashboard
# It builds an archive file that can be used by doing later
#  $ docker run --rm eclipse-che-dashboard | tar -C target/ -zxf -
FROM node:8.15.1

ARG SOURCES_DIR

RUN apt-get update && \
    apt-get install -y git \
    && apt-get -y clean \
    && rm -rf /var/lib/apt/lists/* \
    && yarn global upgrade yarn@1.13.0

# Copy only 3 files and run yarn with them to allow caching in case dependencies hasn't changed
COPY ${SOURCES_DIR}/package.json /dashboard/
COPY ${SOURCES_DIR}/yarn.lock /dashboard/
COPY ${SOURCES_DIR}/typings.json /dashboard/
WORKDIR /dashboard
RUN yarn install --ignore-optional
# Copy all the files (including previously copied) for simplicity of the Dockerfile
COPY ${SOURCES_DIR} /dashboard/
RUN yarn build && yarn test
RUN cd /dashboard/target/ && tar zcf /tmp/dashboard.tar.gz dist/

CMD zcat /tmp/dashboard.tar.gz
