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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.subject.Subject;

/**
 * Provides {@link UserCheTenantData} for a particular user and cheNamespace type in his tenant.
 *
 * @author Oleksandr Garagatyi
 */
@Singleton
public class TenantDataProvider {

  private static final int CACHE_TIMEOUT_MINUTES = 10;
  private static final int CONCURRENT_USERS = 1000;

  private final String cheNamespace;
  private final boolean standalone;
  private final LoadingCache<TenantDataCacheKey, UserCheTenantData> tenantDataCache;

  @Inject
  public TenantDataProvider(
      TenantDataCacheLoader tenantDataCacheLoader,
      @Named("che.infra.openshift.project") String cheNamespace,
      @Named("che.fabric8.standalone") boolean standalone) {
    this.cheNamespace = cheNamespace;
    this.standalone = standalone;
    this.tenantDataCache =
        CacheBuilder.newBuilder()
            .maximumSize(CONCURRENT_USERS)
            .expireAfterWrite(CACHE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build(tenantDataCacheLoader);
  }

  /**
   * Returns OSO namespace information in form of {@link UserCheTenantData}.
   *
   * @param subject subject which tenant data are requested
   * @param namespaceType type of the namespace which tenant data are requested
   * @throws InfrastructureException when any error occurs or there is no namespace of specified
   *     type in user's tenant
   */
  public UserCheTenantData getUserCheTenantData(Subject subject, String namespaceType)
      throws InfrastructureException {
    if (standalone) {
      return getDataStandalone(namespaceType);
    }

    checkSubject(subject);

    // Token is checked in #checkSubject()
    String keycloakToken = subject.getToken();
    try {
      return tenantDataCache.get(new TenantDataCacheKey(keycloakToken, namespaceType));
    } catch (ExecutionException e) {
      throw new InfrastructureException(
          "Exception during the user tenant data retrieval or parsing", e.getCause());
    }
  }

  private UserCheTenantData getDataStandalone(String namespaceType) {
    String namespaceToUse = "che".equals(namespaceType) ? cheNamespace : "myproject";
    return new UserCheTenantData(
        namespaceToUse, "https://kubernetes.default.svc", "dummy.prefix.unused", false);
  }

  private void checkSubject(Subject subject) throws InfrastructureException {
    if (subject == null) {
      throw new InfrastructureException("No Subject is found to perform this action");
    }
    if (subject == Subject.ANONYMOUS) {
      throw new InfrastructureException(
          "The anonymous subject is used, and won't be able to perform this action");
    }
    if (subject.getToken() == null) {
      throw new InfrastructureException("User tenant data is needed but there is no current user");
    }
  }
}
