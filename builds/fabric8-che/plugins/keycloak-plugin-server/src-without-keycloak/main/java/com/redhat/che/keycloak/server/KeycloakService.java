/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package com.redhat.che.keycloak.server;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.eclipse.che.api.core.rest.Service;

import com.google.common.collect.ImmutableMap;

/**
 * Defines Keycloak REST API.
 *
 * @author David Festal
 */
@Path("/keycloak")
public class KeycloakService extends Service {

    private final boolean                       keycloakDisabled;

    @Inject
    public KeycloakService(@Named("che.keycloak.disabled") boolean keycloakDisabled) {
        this.keycloakDisabled = keycloakDisabled;
    }

    @GET
    @Path("/settings")
    @Produces(APPLICATION_JSON)
    public Map<String, String> disabled() {
        return ImmutableMap.of("che.keycloak.disabled", Boolean.toString(keycloakDisabled));
    }
}
