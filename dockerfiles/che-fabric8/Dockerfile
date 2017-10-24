# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

FROM eclipse/che-server:local

# Install pcp - collection basics
# would prefer only pmcd, and not the /bin/pm*tools etc.
COPY pcp.repo /etc/yum.repos.d/pcp.repo
RUN yum install -y pcp patch && yum clean all
COPY ./run-pmcd.sh /run-pmcd.sh
RUN chmod a+x     /run-pmcd.sh
RUN mkdir -p      /etc/pcp /var/run/pcp /var/lib/pcp /var/log/pcp
RUN chmod -R 0777 /etc/pcp /var/run/pcp /var/lib/pcp /var/log/pcp
EXPOSE 44321

COPY entrypoint.patch /entrypoint.patch
RUN patch -p0 < entrypoint.patch

RUN rm -rf /home/user/eclipse-che*
ADD eclipse-che /home/user/eclipse-che
RUN find /home/user -type d -exec chmod 777 {} \;
