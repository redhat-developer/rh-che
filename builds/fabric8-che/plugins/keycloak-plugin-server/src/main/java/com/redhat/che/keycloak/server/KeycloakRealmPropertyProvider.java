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

import com.redhat.che.keycloak.shared.KeycloakConstants;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Injects the 'che.keycloak.realm' property retrieved from the workspace master rest endpoint.
 *
 * @author David Festal
 */
@Singleton
public class KeycloakRealmPropertyProvider extends KeycloakPropertyProvider<String> {

  @Inject
  public KeycloakRealmPropertyProvider(KeycloakSettingsRetriever settingsRetriever) {
    super(settingsRetriever);
  }

  @Override
  protected String propertyName() {
    return KeycloakConstants.REALM_SETTING;
  }

  @Override
  protected String convertProperty(String propertyAsString) {
    return propertyAsString;
  }
}
