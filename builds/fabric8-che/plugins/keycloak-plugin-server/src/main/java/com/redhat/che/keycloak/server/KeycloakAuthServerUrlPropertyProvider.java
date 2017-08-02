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
package com.redhat.che.keycloak.server;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.redhat.che.keycloak.shared.KeycloakConstants;

/**
 * Injects the 'che.keycloak.auth_server_url' property retrieved from the workspace master rest endpoint.
 *
 * @author David Festal
 */
@Singleton
public class KeycloakAuthServerUrlPropertyProvider extends KeycloakPropertyProvider<String> {
    
    @Inject
    public KeycloakAuthServerUrlPropertyProvider(KeycloakSettingsRetriever settingsRetriever) {
        super(settingsRetriever);
    }

    @Override
    protected String propertyName() {
        return KeycloakConstants.AUTH_SERVER_URL_SETTING;
    }
    
    @Override
    protected String convertProperty(String propertyAsString) {
        return propertyAsString;
    }
}
