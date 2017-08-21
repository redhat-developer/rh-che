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
package com.redhat.che.keycloak.shared;

import org.keycloak.common.enums.SslRequired;
import org.keycloak.representations.adapters.config.AdapterConfig;

public class ServicesKeycloakConfigResolver extends AbstractKeycloakConfigResolver {

  @Override
  protected AdapterConfig prepareConfig() {
    AdapterConfig config = new AdapterConfig();
    config.setSslRequired(SslRequired.EXTERNAL.toString().toLowerCase());
    config.setCors(true);
    config.setBearerOnly(true);
    config.setPublicClient(false);
    config.setConnectionPoolSize(20);
    config.setDisableTrustManager(true);
    config.setEnableBasicAuth(false);
    config.setEnableBasicAuth(false);
    config.setExposeToken(true);
    return config;
  }
}
