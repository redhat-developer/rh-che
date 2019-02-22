/*
 * Copyright (c) 2016-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.che.multitenant.tenantdata;

import java.util.Objects;

class TenantDataCacheKey {

  private final String keycloakToken;
  private final String namespaceType;

  TenantDataCacheKey(String keycloakToken, String namespaceType) {
    this.keycloakToken = keycloakToken;
    this.namespaceType = namespaceType;
  }

  public String getKeycloakToken() {
    return keycloakToken;
  }

  public String getNamespaceType() {
    return namespaceType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TenantDataCacheKey)) {
      return false;
    }
    TenantDataCacheKey cacheKey = (TenantDataCacheKey) o;
    return Objects.equals(keycloakToken, cacheKey.keycloakToken)
        && Objects.equals(namespaceType, cacheKey.namespaceType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(keycloakToken, namespaceType);
  }
}
