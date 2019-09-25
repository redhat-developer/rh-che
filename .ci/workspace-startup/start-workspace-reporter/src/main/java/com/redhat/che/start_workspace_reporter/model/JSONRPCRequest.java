/*
 * Copyright (c) 2018-2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.che.start_workspace_reporter.model;

import com.google.gson.JsonElement;

public class JSONRPCRequest {

  private String jsonrpc;
  private String method;
  private Integer id;
  private String auth;
  private JsonElement params;

  public JSONRPCRequest() {}

  public JSONRPCRequest(
      String jsonrpc, String method, Integer id, String auth, JsonElement params) {
    this.jsonrpc = jsonrpc;
    this.method = method;
    this.id = id;
    this.auth = auth;
    this.params = params;
  }

  public String getJsonrpc() {
    return jsonrpc;
  }

  public void setJsonrpc(String jsonrpc) {
    this.jsonrpc = jsonrpc;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getAuth() {
    return auth;
  }

  public void setAuth(String auth) {
    this.auth = auth;
  }

  public JsonElement getParams() {
    return params;
  }

  public void setParams(JsonElement params) {
    this.params = params;
  }

  @Override
  public String toString() {
    StringBuilder request = new StringBuilder();
    request.append("{");
    request.append("\"jsonrpc\":\"").append(this.jsonrpc).append("\",");
    request.append("\"method\":\"").append(this.method).append("\",");
    if (this.auth != null) request.append("\"auth\":\"").append(this.auth).append("\",");
    request.append("\"params\":").append(this.params.toString()).append(",");
    request.append("\"id\":").append(this.id);
    request.append("}");
    return request.toString();
  }
}
