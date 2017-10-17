/*
 * Copyright (c) 2012-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.che.multitenant;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.plugin.docker.client.WorkspacesRoutingSuffixProvider;
import org.eclipse.che.plugin.openshift.client.exception.OpenShiftException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrieves a routing suffix that can be used when building workspace agents external address with
 * a custom server evaluation strategy. This is similar to the 'che.docker.ip.external' property,
 * but specific to the workspaces and dynamic (which for example depend on the current user). So
 * this is mainly useful when workspaces are not hosted on the same machine as the main Che server
 * and workspace master.
 *
 * <p>The default implementation returns null. But it is expected that extensions, such alternate
 * docker connectors, would provide a convenient non-null value.
 *
 * @author David Festal
 */
@Singleton
public class UserBasedWorkspacesRoutingSuffixProvider extends WorkspacesRoutingSuffixProvider {
  private static final Logger LOG =
      LoggerFactory.getLogger(UserBasedWorkspacesRoutingSuffixProvider.class);

  private String cheWorkspacesRoutingSuffix;

  @Inject private Fabric8WorkspaceEnvironmentProvider workspaceEnvironmentProvider;

  @Inject
  public UserBasedWorkspacesRoutingSuffixProvider(
      @Nullable @Named("che.fabric8.workspaces.routing_suffix") String cheWorkspacesRoutingSuffix) {

    LOG.info("Workspaces Routing Suffix = {}", cheWorkspacesRoutingSuffix);
    this.cheWorkspacesRoutingSuffix =
        "NULL".equals(cheWorkspacesRoutingSuffix) ? null : cheWorkspacesRoutingSuffix;
  }

  @Override
  @Nullable
  public String get() {
    String suffix = null;
    try {
      suffix = workspaceEnvironmentProvider.getUserCheTenantData().getRoutePrefix();
    } catch (OpenShiftException e) {
      LOG.warn("Exception when trying to retrieve routing suffix from user tenant data", e);
    }
    if (suffix != null) {
      return suffix;
    }
    return cheWorkspacesRoutingSuffix;
  }
}
