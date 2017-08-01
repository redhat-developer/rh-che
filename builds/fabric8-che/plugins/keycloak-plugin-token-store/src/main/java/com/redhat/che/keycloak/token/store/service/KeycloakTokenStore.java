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
package com.redhat.che.keycloak.token.store.service;

import javax.inject.Singleton;

@Singleton
/**
 * Shared service to store and retrieve the latest keycloak token being used.
 */
public class KeycloakTokenStore {

    private String lastToken;

    public String getLastToken() {
        return lastToken;
    }

    public boolean hasLastToken() {
        return lastToken != null;
    }

    public void setLastToken(String lastToken) {
        this.lastToken = lastToken;
    }

    public String getLastTokenWithoutBearer() {
        if (lastToken == null) {
            return null;
        }
        return lastToken.replace("Bearer ", "");
    }
}
