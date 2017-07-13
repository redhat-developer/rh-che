/*******************************************************************************
 * Copyright (c) 2017 Red Hat inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat - Initial Contribution
 *******************************************************************************/
package com.redhat.che.keycloak.server.oso.service.account;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;

@Singleton
public class ServiceAccountInfoProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceAccountInfoProvider.class);

    private String token;
    private String namespace;

    @PostConstruct
    public void init() {
        try (OpenShiftClient client = new DefaultOpenShiftClient()) {
            this.token = client.getConfiguration().getOauthToken();
            this.namespace = client.getConfiguration().getNamespace();
            LOG.debug("Service account namespace: '{}'", namespace);
        }
    }

    public String getToken() {
        return token;
    }

    public String getNamespace() {
        return namespace;
    }

}
