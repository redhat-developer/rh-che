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

import javax.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class for providers that inject a Keycloak property retrieved from the workspace master
 * rest endpoint.
 *
 * @author David Festal
 */
public abstract class KeycloakPropertyProvider<Type> implements Provider<Type> {
  private Type property = null;
  private KeycloakSettingsRetriever settingsRetriever;

  private static final Logger LOG = LoggerFactory.getLogger(KeycloakPropertyProvider.class);

  public KeycloakPropertyProvider(KeycloakSettingsRetriever settingsRetriever) {
    this.settingsRetriever = settingsRetriever;
  }

  protected abstract String propertyName();

  protected abstract Type convertProperty(String propertyAsString);

  @Override
  public Type get() {
    if (property == null) {
      String propertyName = propertyName();
      String propertyAsString = settingsRetriever.getSettings().get(propertyName);
      if (propertyAsString != null) {
        property = convertProperty(propertyAsString);
        LOG.info("Property '" + propertyName + "' ==> " + property);
      }
    }
    return property;
  }
}
