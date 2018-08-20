#
# Copyright (c) 2016-2018 Red Hat, Inc.
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#
# Contributors:
#   Red Hat, Inc. - initial API and implementation
#

if [ -n "${CHE_OSO_USER_TOKEN}" ] && [ -n "${CHE_OSO_PROJECT}" ] && [ -n "${CHE_OSO_CLUSTER}" ]; then
    # login to OSO project where user has edit rights
    oc login ${CHE_OSO_CLUSTER} --insecure-skip-tls-verify=${CHE_OSO_TRUST_CERTS} --token=${CHE_OSO_USER_TOKEN} && oc project ${CHE_OSO_PROJECT} || true
fi
