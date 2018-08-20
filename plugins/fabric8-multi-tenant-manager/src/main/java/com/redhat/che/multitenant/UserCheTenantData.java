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
package com.redhat.che.multitenant;

public class UserCheTenantData {
  private String namespace;
  private String clusterUrl;
  private String routePrefix;
  private boolean clusterCapacityExhausted;

  public UserCheTenantData(
      String namespace, String clusterUrl, String routePrefix, boolean clusterCapacityExhausted) {
    this.namespace = namespace;
    this.clusterUrl = clusterUrl;
    this.routePrefix = routePrefix;
    this.clusterCapacityExhausted = clusterCapacityExhausted;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getClusterUrl() {
    return clusterUrl;
  }

  public String getRouteBaseSuffix() {
    return routePrefix;
  }

  public boolean isClusterCapacityExhausted() {
    return clusterCapacityExhausted;
  }

  public void setClusterCapacityExhausted(boolean clusterCapacityExhausted) {
    this.clusterCapacityExhausted = clusterCapacityExhausted;
  }

  @Override
  public String toString() {
    return "{"
        + namespace
        + ","
        + clusterUrl
        + ","
        + routePrefix
        + ","
        + clusterCapacityExhausted
        + "}";
  }
}
