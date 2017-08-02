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
package com.redhat.che.keycloak.shared;

import java.util.Map;
import java.util.logging.Logger;

import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.spi.HttpFacade.Request;
import org.keycloak.representations.adapters.config.AdapterConfig;

public abstract class AbstractKeycloakConfigResolver implements KeycloakConfigResolver {
    
    private static final Logger LOG = Logger.getLogger(AbstractKeycloakConfigResolver.class.getName());

    private KeycloakDeployment keycloakDeployment = null;
    
    protected abstract AdapterConfig prepareConfig();
    
    @Override
    public KeycloakDeployment resolve(Request facade) {
        if (keycloakDeployment != null) {
            return keycloakDeployment;
        }
        Map<String, String> settings = KeycloakSettings.get();
        if (settings == null) {
            LOG.severe("Keycloak settings are not set: Keycloak cannot be correctly initialized");
            throw new RuntimeException("Keycloak settings are not set: Keycloak cannot be correctly initialized");
        }
        AdapterConfig config = prepareConfig();
        config.setAuthServerUrl(settings.get(KeycloakConstants.AUTH_SERVER_URL_SETTING));
        config.setRealm(settings.get(KeycloakConstants.REALM_SETTING));
        config.setResource(settings.get(KeycloakConstants.CLIENT_ID_SETTING));
        keycloakDeployment = KeycloakDeploymentBuilder.build(config);
        return keycloakDeployment;
    }
}
