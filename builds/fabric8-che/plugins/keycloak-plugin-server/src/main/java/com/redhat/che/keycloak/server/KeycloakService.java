/*
 * Copyright (c) 2016-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.che.keycloak.server;

import static com.redhat.che.keycloak.shared.KeycloakConstants.*;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import com.google.common.collect.ImmutableMap;
import com.redhat.che.keycloak.shared.KeycloakSettings;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.commons.annotation.Nullable;

/**
 * Defines Keycloak REST API.
 *
 * @author David Festal
 */
@Singleton
@Path("/keycloak")
public class KeycloakService extends Service {

  @Inject
  public KeycloakService(
      @Named(DISABLED_SETTING) boolean keycloakDisabled,
      @Named(AUTH_SERVER_URL_SETTING) String serverURL,
      @Named(REALM_SETTING) String realm,
      @Named(CLIENT_ID_SETTING) String clientId,
      @Nullable @Named(OSO_ENDPOINT_SETTING) String osoEndpoint,
      @Nullable @Named(GITHUB_ENDPOINT_SETTING) String gitHubEndpoint) {
    KeycloakSettings.set(
        ImmutableMap.<String, String>builder()
            .put(DISABLED_SETTING, Boolean.toString(keycloakDisabled))
            .put(AUTH_SERVER_URL_SETTING, serverURL)
            .put(CLIENT_ID_SETTING, clientId)
            .put(REALM_SETTING, realm)
            .put(OSO_ENDPOINT_SETTING, osoEndpoint)
            .put(GITHUB_ENDPOINT_SETTING, gitHubEndpoint)
            .build());
  }

  @GET
  @Path("/settings")
  @Produces(APPLICATION_JSON)
  public Map<String, String> settings() {
    return KeycloakSettings.get();
  }
}
