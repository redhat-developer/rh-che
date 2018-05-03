/*
 * Copyright (c) 2016-2018 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
