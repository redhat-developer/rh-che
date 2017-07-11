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
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

import org.eclipse.che.api.core.rest.DefaultHttpJsonRequestFactory;
import org.eclipse.che.api.core.rest.HttpJsonRequest;
import org.eclipse.che.api.core.rest.shared.dto.Link;

@Singleton
public class KeycloakHttpJsonRequestFactory extends DefaultHttpJsonRequestFactory {

    private boolean keycloakDisabled;

    @Inject
    public KeycloakHttpJsonRequestFactory(@Named("che.keycloak.disabled") boolean keycloakDisabled) {
        this.keycloakDisabled = keycloakDisabled;
    }

    @Override
    public HttpJsonRequest fromUrl(@NotNull String url) {
        if (keycloakDisabled) {
            return super.fromUrl(url);
        }
        return super.fromUrl(url).setAuthorizationHeader("Internal");
    }

    @Override
    public HttpJsonRequest fromLink(@NotNull Link link) {
        if (keycloakDisabled) {
            return super.fromLink(link);
        }
        return super.fromLink(link).setAuthorizationHeader("Internal");
    }

}