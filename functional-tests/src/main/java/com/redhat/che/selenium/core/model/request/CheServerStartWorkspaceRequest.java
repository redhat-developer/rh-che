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
import org.eclipse.che.dto.server.JsonSerializable;

public class CheServerStartWorkspaceRequest implements JsonSerializable {

  private static final Gson GSON = new Gson();

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
}
