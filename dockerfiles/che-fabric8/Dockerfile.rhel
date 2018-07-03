# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

# RHEL parent dockerfile: https://github.com/rhdt/EL-Dockerfiles/blob/master/base/rh-che/Dockerfile

FROM quay.io/openshiftio/rhel-base-rh-che:latest

ENV LANG="C.UTF-8" \
    CHE_HOME=/home/user/che \
    CHE_IN_CONTAINER=true

EXPOSE 8000 8080

RUN mkdir /logs /data && \
    chmod 0777 /logs /data

COPY ./run-pmcd.sh /run-pmcd.sh
RUN chmod a+x     /run-pmcd.sh
RUN mkdir -p      /etc/pcp /var/run/pcp /var/lib/pcp /var/log/pcp
RUN chmod -R 0777 /etc/pcp /var/run/pcp /var/lib/pcp /var/log/pcp
EXPOSE 44321

COPY entrypoint.sh /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]

ADD eclipse-che /home/user/eclipse-che
RUN find /home/user -type d -exec chmod 777 {} \;
