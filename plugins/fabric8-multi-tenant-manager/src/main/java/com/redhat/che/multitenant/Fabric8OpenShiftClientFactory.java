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

import com.google.inject.Provider;
import com.redhat.che.multitenant.toggle.CheServiceAccountTokenToggle;
import io.fabric8.kubernetes.client.Config;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import okhttp3.EventListener;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.workspace.server.WorkspaceRuntimes;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.RuntimeContext;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.workspace.infrastructure.kubernetes.cache.KubernetesRuntimeStateCache;
import org.eclipse.che.workspace.infrastructure.kubernetes.model.KubernetesRuntimeState;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author Sergii Leshchenko */
@Singleton
public class Fabric8OpenShiftClientFactory extends OpenShiftClientFactory {

  private final Fabric8WorkspaceEnvironmentProvider envProvider;
  private final Provider<WorkspaceRuntimes> workspaceRuntimeProvider;
  private final WorkspaceSubjectsRegistry subjectsRegistry;
  private final KubernetesRuntimeStateCache runtimeStateCache;
  private final CheServiceAccountTokenToggle cheServiceAccountTokenToggle;

  private static final Logger LOG = LoggerFactory.getLogger(Fabric8OpenShiftClientFactory.class);

  @Inject
  public Fabric8OpenShiftClientFactory(
      Fabric8WorkspaceEnvironmentProvider envProvider,
      Provider<WorkspaceRuntimes> workspaceRuntimeProvider,
      WorkspaceSubjectsRegistry subjectsRegistry,
      KubernetesRuntimeStateCache runtimeStateCache,
      CheServiceAccountTokenToggle cheServiceAccountTokenToggle,
      @Nullable @Named("che.infra.kubernetes.trust_certs") Boolean doTrustCerts,
      @Named("che.infra.kubernetes.client.http.async_requests.max") int maxConcurrentRequests,
      @Named("che.infra.kubernetes.client.http.async_requests.max_per_host")
          int maxConcurrentRequestsPerHost,
      @Named("che.infra.kubernetes.client.http.connection_pool.max_idle") int maxIdleConnections,
      @Named("che.infra.kubernetes.client.http.connection_pool.keep_alive_min")
          int connectionPoolKeepAlive,
      EventListener eventListener) {
    super(
        null,
        null,
        doTrustCerts != null ? doTrustCerts.booleanValue() : false,
        maxConcurrentRequests,
        maxConcurrentRequestsPerHost,
        maxIdleConnections,
        connectionPoolKeepAlive,
        eventListener);
    this.envProvider = envProvider;
    this.workspaceRuntimeProvider = workspaceRuntimeProvider;
    this.subjectsRegistry = subjectsRegistry;
    this.runtimeStateCache = runtimeStateCache;
    this.cheServiceAccountTokenToggle = cheServiceAccountTokenToggle;
  }

  @Override
  public Config buildConfig(Config defaultConfig, @Nullable String workspaceId)
      throws InfrastructureException {
    Subject subject = EnvironmentContext.getCurrent().getSubject();

    // If there is no way to get a subject, we return the default config
    // as getting the config below will throw an error.
    if (workspaceId == null && (subject == null || subject.isAnonymous())) {
      return defaultConfig;
    }

    if (workspaceId != null) {
      Optional<RuntimeIdentity> runtimeIdentity = getRuntimeIdentity(workspaceId);
      if (runtimeIdentity.isPresent()) {
        String userIdFromWorkspaceId = runtimeIdentity.get().getOwnerId();
        if (!userIdFromWorkspaceId.equals(subject.getUserId())) {
          try {
            subject = subjectsRegistry.getSubject(userIdFromWorkspaceId);
          } catch (NotFoundException e) {
            Optional<KubernetesRuntimeState> runtimeState =
                runtimeStateCache.get(runtimeIdentity.get());
            if (cheServiceAccountTokenToggle.useCheServiceAccountToken(userIdFromWorkspaceId)
                && runtimeState.isPresent()) {
              LOG.debug(
                  "Current user ID ('{}') is different from the user ID ('{}') that started workspace {} "
                      + "and subject of user that started workspace is not found in subjects cache.\n"
                      + "Let's search inside the running workspace runtime state, if any, to find the user namespace",
                  subject.getUserId(),
                  userIdFromWorkspaceId,
                  workspaceId);
              subject =
                  new GuessedSubject(userIdFromWorkspaceId, runtimeState.get().getNamespace());
            } else {
              LOG.warn(
                  "Current user ID ('{}') is different from the user ID ('{}') that started workspace {} "
                      + "and subject of user that started workspace is not found in subjects cache",
                  subject.getUserId(),
                  userIdFromWorkspaceId,
                  workspaceId);
            }
          }
        }
      } else { // !runtimeIdentity.isPresent()
        LOG.warn(
            "Could not get runtimeIdentity for workspace '{}', and so cannot verify "
                + "if current subject '{}' owns workspace",
            workspaceId,
            subject.getUserId());
      }
    }
    return envProvider.getWorkspacesOpenshiftConfig(subject);
  }

  private Optional<RuntimeIdentity> getRuntimeIdentity(String workspaceId) {
    @SuppressWarnings("rawtypes")
    Optional<RuntimeContext> context =
        workspaceRuntimeProvider.get().getRuntimeContext(workspaceId);
    return context.map(c -> c.getIdentity());
  }
}
