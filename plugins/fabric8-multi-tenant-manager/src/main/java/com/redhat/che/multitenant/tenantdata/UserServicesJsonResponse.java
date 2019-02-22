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

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class UserServicesJsonResponse {

  @SerializedName("data")
  private Data data;

  public Data getData() {
    return data;
  }

  public void setData(Data data) {
    this.data = data;
  }

  /**
   * Get the descendant namespaces node.
   *
   * @throws NullPointerException if data could not be deserialized (e.g. attributes is null)
   */
  public List<Namespace> getNamespaces() {
    return data.getAttributes().getNamespaces();
  }

  public static class Data {
    @SerializedName("attributes")
    private Attributes attributes;

    public Attributes getAttributes() {
      return attributes;
    }

    public void setAttributes(Attributes attributes) {
      this.attributes = attributes;
    }
  }

  public static class Attributes {
    @SerializedName("namespaces")
    private List<Namespace> namespaces;

    public List<Namespace> getNamespaces() {
      return namespaces;
    }

    public void setNamespaces(List<Namespace> namespaces) {
      this.namespaces = namespaces;
    }
  }

  public static class Namespace {
    @SerializedName("name")
    private String name;

    @SerializedName("type")
    private String type;

    @SerializedName("cluster-app-domain")
    private String clusterAppDomain;

    @SerializedName("cluster-url")
    private String clusterUrl;

    @SerializedName("cluster-capacity-exhausted")
    private boolean clusterCapacityExhausted;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getClusterAppDomain() {
      return clusterAppDomain;
    }

    public void setClusterAppDomain(String clusterAppDomain) {
      this.clusterAppDomain = clusterAppDomain;
    }

    public String getClusterUrl() {
      return clusterUrl;
    }

    public void setClusterUrl(String clusterUrl) {
      this.clusterUrl = clusterUrl;
    }

    public boolean isClusterCapacityExhausted() {
      return clusterCapacityExhausted;
    }

    public void setClusterCapacityExhausted(boolean clusterCapacityExhausted) {
      this.clusterCapacityExhausted = clusterCapacityExhausted;
    }
  }
}
