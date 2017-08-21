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
package org.eclipse.che.wsagent.server;

import static java.util.Collections.singletonMap;

import com.google.inject.name.Names;
import com.google.inject.servlet.ServletModule;
import com.redhat.che.keycloak.server.KeycloakAuthServerUrlPropertyProvider;
import com.redhat.che.keycloak.server.KeycloakAuthenticationFilter;
import com.redhat.che.keycloak.server.KeycloakClientIdPropertyProvider;
import com.redhat.che.keycloak.server.KeycloakDisabledPropertyProvider;
import com.redhat.che.keycloak.server.KeycloakRealmPropertyProvider;
import com.redhat.che.keycloak.shared.KeycloakConstants;
import com.redhat.che.keycloak.shared.ServicesKeycloakConfigResolver;
import javax.inject.Singleton;
import org.eclipse.che.api.core.cors.CheCorsFilter;
import org.eclipse.che.inject.DynaModule;
import org.everrest.guice.servlet.GuiceEverrestServlet;

/** @author andrew00x */
@DynaModule
public class RedHatCheWsAgentServletModule extends ServletModule {
  @Override
  protected void configureServlets() {
    filter("/*").through(CheCorsFilter.class);
    serveRegex("^/api((?!(/(ws|eventbus)($|/.*)))/.*)").with(GuiceEverrestServlet.class);

    bind(Boolean.class)
        .annotatedWith(Names.named(KeycloakConstants.DISABLED_SETTING))
        .toProvider(KeycloakDisabledPropertyProvider.class);
    bind(String.class)
        .annotatedWith(Names.named(KeycloakConstants.AUTH_SERVER_URL_SETTING))
        .toProvider(KeycloakAuthServerUrlPropertyProvider.class);
    bind(String.class)
        .annotatedWith(Names.named(KeycloakConstants.CLIENT_ID_SETTING))
        .toProvider(KeycloakClientIdPropertyProvider.class);
    bind(String.class)
        .annotatedWith(Names.named(KeycloakConstants.REALM_SETTING))
        .toProvider(KeycloakRealmPropertyProvider.class);
    bind(KeycloakAuthenticationFilter.class).in(Singleton.class);
    filter("/*")
        .through(
            KeycloakAuthenticationFilter.class,
            singletonMap(
                "keycloak.config.resolver", ServicesKeycloakConfigResolver.class.getName()));
  }
}
