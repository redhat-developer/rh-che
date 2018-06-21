# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

FROM ceylon/ceylon:1.3.3-redhat
ADD .ceylon/ .ceylon/
ADD source/ source/
RUN ceylon compile keycloak_configurator
CMD ceylon run keycloak_configurator