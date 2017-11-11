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

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.redhat.che.keycloak.server.KeycloakAuthServerUrlPropertyProvider;
import com.redhat.che.keycloak.server.KeycloakClientIdPropertyProvider;
import com.redhat.che.keycloak.server.KeycloakDisabledPropertyProvider;
import com.redhat.che.keycloak.server.KeycloakHttpJsonRequestFactory;
import com.redhat.che.keycloak.server.KeycloakRealmPropertyProvider;
import com.redhat.che.keycloak.shared.KeycloakConstants;
import com.redhat.che.keycloak.token.store.service.KeycloakTokenStore;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.inject.DynaModule;

/**
 * Mandatory modules of workspace agent
 *
 * @author Evgen Vidolob
 * @author Sergii Kabashniuk
 */
@DynaModule
public class WsAgentMachineAuthModule extends AbstractModule {

  @Override
  protected void configure() {
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
    bind(HttpJsonRequestFactory.class).to(KeycloakHttpJsonRequestFactory.class);
    bind(KeycloakTokenStore.class);
  }
}
