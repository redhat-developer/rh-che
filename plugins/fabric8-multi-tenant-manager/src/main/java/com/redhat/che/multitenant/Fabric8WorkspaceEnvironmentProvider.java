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

import com.redhat.che.multitenant.multicluster.MultiClusterOpenShiftProxy;
import com.redhat.che.multitenant.toggle.CheServiceAccountTokenToggle;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.inject.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Fabric8WorkspaceEnvironmentProvider {

  private static final Logger LOG =
      LoggerFactory.getLogger(Fabric8WorkspaceEnvironmentProvider.class);

  private final MultiClusterOpenShiftProxy multiClusterOpenShiftProxy;
  private final CheServiceAccountTokenToggle cheServiceAccountTokenToggle;
  private final TenantDataProvider tenantDataProvider;

  @Inject
  public Fabric8WorkspaceEnvironmentProvider(
      @Named("che.fabric8.multitenant") boolean fabric8CheMultitenant,
      MultiClusterOpenShiftProxy multiClusterOpenShiftProxy,
      CheServiceAccountTokenToggle cheServiceAccountTokenToggle,
      TenantDataProvider tenantDataProvider) {
    if (!fabric8CheMultitenant) {
      throw new ConfigurationException(
          "Fabric8 Che Multitetant is disabled. "
              + "che.infra.openshift.project must be configured with non null value");
    }
    this.multiClusterOpenShiftProxy = multiClusterOpenShiftProxy;
    this.cheServiceAccountTokenToggle = cheServiceAccountTokenToggle;
    this.tenantDataProvider = tenantDataProvider;
  }

  public Config getWorkspacesOpenshiftConfig(Subject subject) throws InfrastructureException {
    checkSubject(subject);

    String keycloakToken = subject.getToken();

    UserCheTenantData cheTenantData = getUserCheTenantData(subject);

    String osoProxyUrl = multiClusterOpenShiftProxy.getUrl();
    LOG.debug("OSO proxy URL - {}", osoProxyUrl);
    String userId = subject.getUserId();

    if (cheServiceAccountTokenToggle.useCheServiceAccountToken(userId)) {
      LOG.debug("Using Che SA token for '{}'", userId);
      // TODO provide Config to oso proxy which will use Che SA token obtained from fabric-auth and
      // userId as username
    }

    return new ConfigBuilder()
        .withMasterUrl(osoProxyUrl)
        .withOauthToken(keycloakToken)
        .withNamespace(cheTenantData.getNamespace())
        .withTrustCerts(true)
        .build();
  }

  public String getWorkspacesOpenshiftNamespace(Subject subject) throws InfrastructureException {
    checkSubject(subject);

    return getUserCheTenantData(subject).getNamespace();
  }

  private void checkSubject(Subject subject) throws InfrastructureException {
    if (subject == null) {
      throw new InfrastructureException("No Subject is found to perform this action");
    }
    if (subject == Subject.ANONYMOUS) {
      throw new InfrastructureException(
          "The anonymous subject is used, and won't be able to perform this action");
    }
  }

  private UserCheTenantData getUserCheTenantData(Subject subject) throws InfrastructureException {
    UserCheTenantData tenantData = tenantDataProvider.getUserCheTenantData(subject, "che");
    return new UserCheTenantData(
        tenantData.getNamespace(),
        multiClusterOpenShiftProxy.getUrl(),
        tenantData.getRouteBaseSuffix());
  }
}
