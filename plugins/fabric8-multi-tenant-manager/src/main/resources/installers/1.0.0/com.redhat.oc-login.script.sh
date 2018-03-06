#
# Copyright (c) 2016-2018 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#   Red Hat, Inc. - initial API and implementation
#

if [ -n "${CHE_OSO_USER_TOKEN}" ] && [ -n "${CHE_OSO_PROJECT}" ] && [ -n "${CHE_OSO_CLUSTER}" ]; then
    # login to OSO project where user has edit rights
    oc login ${CHE_OSO_CLUSTER} --token=${CHE_OSO_USER_TOKEN} && oc project ${CHE_OSO_PROJECT} || true
fi
