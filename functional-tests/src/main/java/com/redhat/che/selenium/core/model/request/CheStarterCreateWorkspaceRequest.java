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
package com.redhat.che.selenium.core.model.request;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.io.Writer;
import java.util.Objects;
import org.eclipse.che.dto.server.JsonSerializable;

public class CheStarterCreateWorkspaceRequest implements JsonSerializable {

  private static final Gson GSON = new Gson();

  protected String branch;
  protected String description;
  protected String repo;
  protected String stackId;

  @Override
  public String toJson() {
    return GSON.toJson(this);
  }

  @Override
  public void toJson(Writer writer) {
    GSON.toJson(this, writer);
  }

  @Override
  public JsonElement toJsonElement() {
    return GSON.toJsonTree(this);
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getRepo() {
    return repo;
  }

  public void setRepo(String repo) {
    this.repo = repo;
  }

  public String getStackId() {
    return stackId;
  }

  public void setStackId(String stackId) {
    this.stackId = stackId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CheStarterCreateWorkspaceRequest that = (CheStarterCreateWorkspaceRequest) o;
    return Objects.equals(getBranch(), that.getBranch())
        && Objects.equals(getDescription(), that.getDescription())
        && Objects.equals(getRepo(), that.getRepo())
        && Objects.equals(getStackId(), that.getStackId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getBranch(), getDescription(), getRepo(), getStackId());
  }
}
