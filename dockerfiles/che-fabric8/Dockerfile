# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
ARG CHE_DASHBOARD_VERSION=next
ARG CHE_WORKSPACE_LOADER_VERSION=next

FROM quay.io/eclipse/che-dashboard:${CHE_DASHBOARD_VERSION} as che_dashboard_base
FROM quay.io/eclipse/che-workspace-loader:${CHE_WORKSPACE_LOADER_VERSION} as che_workspace_loader_base


FROM registry.centos.org/centos/centos:latest

# The following lines are needed to set the correct locale after `yum update`
# c.f. https://github.com/CentOS/sig-cloud-instance-images/issues/71
RUN localedef -i en_US -f UTF-8 C.UTF-8

ENV LANG="C.UTF-8" \
    JAVA_HOME=/usr/lib/jvm/java-11-openjdk \
    PATH=${PATH}:${JAVA_HOME}/bin \
    CHE_HOME=/home/user/che \
    CHE_IN_CONTAINER=true

RUN yum -y update && \
    yum -y install openssl java-11-openjdk-devel sudo skopeo && \
    yum clean all && \
    echo "%root ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers && \
    sed -i 's/Defaults    requiretty/#Defaults    requiretty/g' /etc/sudoers && \
    rm -rf /tmp/* /var/cache/yum

EXPOSE 8000 8080

RUN mkdir /logs /data && \
    chmod 0777 /logs /data

# Install pcp - collection basics
# would prefer only pmcd, and not the /bin/pm*tools etc.
COPY pcp.repo /etc/yum.repos.d/pcp.repo
RUN yum install -y pcp patch && yum clean all
COPY ./run-pmcd.sh /run-pmcd.sh
RUN chmod a+x     /run-pmcd.sh
RUN mkdir -p      /etc/pcp /var/run/pcp /var/lib/pcp /var/log/pcp
RUN chmod -R 0777 /etc/pcp /var/run/pcp /var/lib/pcp /var/log/pcp
EXPOSE 44321

COPY entrypoint.sh /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]

COPY --from=che_workspace_loader_base /usr/local/apache2/htdocs/workspace-loader/ /home/user/eclipse-che/tomcat/webapps/workspace-loader
COPY --from=che_dashboard_base /usr/local/apache2/htdocs/dashboard /home/user/eclipse-che/tomcat/webapps/dashboard
ADD eclipse-che /home/user/eclipse-che
# Let's apply Hosted Che specific 'product.json' for the dashboard customization
COPY assets/branding/product.json /home/user/eclipse-che/tomcat/webapps/dashboard/assets/branding/

RUN find /home/user -type d -exec chmod 777 {} \;
