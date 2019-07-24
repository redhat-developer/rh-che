# Copyright (c) 2019 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

FROM docker.io/eclipse/che-e2e:7.0.0-rc-3.0

ENV LANG=en_US.utf8 \
    DISPLAY=:99 \
    FABRIC8_USER_NAME=fabric8
    
RUN cd e2e && \
    npm run tsc && \
    #Download rh-che
    cd ../ && \
    git clone https://github.com/redhat-developer/rh-che.git && \
    cd rh-che/e2e-saas && \
    npm --silent i
    
WORKDIR /root/
COPY docker-entrypoint.sh /root/

ENTRYPOINT ["/root/docker-entrypoint.sh"]
