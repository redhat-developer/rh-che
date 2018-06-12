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

import com.google.inject.Provider;
import io.fabric8.kubernetes.client.Config;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.workspace.server.WorkspaceRuntimes;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.RuntimeContext;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author Sergii Leshchenko */
@Singleton
public class Fabric8OpenShiftClientFactory extends OpenShiftClientFactory {

  private final Fabric8WorkspaceEnvironmentProvider envProvider;
  private final Provider<WorkspaceRuntimes> workspaceRuntimeProvider;
  private final WorkspaceSubjectsRegistry subjectsRegistry;

  private static final Logger LOG = LoggerFactory.getLogger(Fabric8OpenShiftClientFactory.class);

  @Inject
  public Fabric8OpenShiftClientFactory(
      Fabric8WorkspaceEnvironmentProvider envProvider,
      Provider<WorkspaceRuntimes> workspaceRuntimeProvider,
      WorkspaceSubjectsRegistry subjectsRegistry,
      @Nullable @Named("che.infra.kubernetes.trust_certs") Boolean doTrustCerts,
      @Named("che.infra.kubernetes.client.http.async_requests.max") int maxConcurrentRequests,
      @Named("che.infra.kubernetes.client.http.async_requests.max_per_host")
          int maxConcurrentRequestsPerHost,
      @Named("che.infra.kubernetes.client.http.connection_pool.max_idle") int maxIdleConnections,
      @Named("che.infra.kubernetes.client.http.connection_pool.keep_alive_min")
          int connectionPoolKeepAlive) {
    super(
        null,
        null,
        null,
        null,
        null,
        doTrustCerts != null ? doTrustCerts.booleanValue() : false,
        maxConcurrentRequests,
        maxConcurrentRequestsPerHost,
        maxIdleConnections,
        connectionPoolKeepAlive);
    this.envProvider = envProvider;
    this.workspaceRuntimeProvider = workspaceRuntimeProvider;
    this.subjectsRegistry = subjectsRegistry;
  }

  @Override
  public Config buildConfig(Config defaultConfig, @Nullable String workspaceId)
      throws InfrastructureException {
    Subject subject = EnvironmentContext.getCurrent().getSubject();
    if (workspaceId != null) {
      Optional<String> userIdFromWorkspaceId = getUserIdForWorkspaceId(workspaceId);
      if (userIdFromWorkspaceId.isPresent()) {
        if (!userIdFromWorkspaceId.get().equals(subject.getUserId())) {
          try {
            subject = subjectsRegistry.getSubject(userIdFromWorkspaceId.get());
          } catch (NotFoundException e) {
            LOG.warn(
                "Current user ID ('{}') is different from the user ID ('{}') that started workspace {} "
                    + "and subject of user that started workspace is not found in subjects cache",
                subject.getUserId(),
                userIdFromWorkspaceId.get(),
                workspaceId);
          }
        }
      }
    }
    return envProvider.getWorkspacesOpenshiftConfig(subject);
  }

  private Optional<String> getUserIdForWorkspaceId(String workspaceId) {
    Optional<RuntimeContext> context =
        workspaceRuntimeProvider.get().getRuntimeContext(workspaceId);
    return context.map(c -> c.getIdentity().getOwnerId());
  }
}
