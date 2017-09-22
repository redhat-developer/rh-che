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

public class KeycloakConstants {

  public static final String KEYCLOAK_SETTINGS_ENDPOINT_PATH = "/keycloak/settings";

  public static final String KEYCLOAK_SETTING_PREFIX = "che.keycloak.";
  public static final String DISABLED_SETTING = KEYCLOAK_SETTING_PREFIX + "disabled";
  public static final String AUTH_SERVER_URL_SETTING = KEYCLOAK_SETTING_PREFIX + "auth_server_url";
  public static final String REALM_SETTING = KEYCLOAK_SETTING_PREFIX + "realm";
  public static final String CLIENT_ID_SETTING = KEYCLOAK_SETTING_PREFIX + "client_id";

  public static final String OSO_ENDPOINT_SETTING = KEYCLOAK_SETTING_PREFIX + "oso.endpoint";
  public static final String GITHUB_ENDPOINT_SETTING = KEYCLOAK_SETTING_PREFIX + "github.endpoint";

  public static final String getEndpoint(String apiEndpoint) {
    return apiEndpoint + KEYCLOAK_SETTINGS_ENDPOINT_PATH;
  }
}
