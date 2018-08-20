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

import static java.lang.String.format;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.core.rest.HttpJsonResponse;
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

  private final boolean standalone;
  private final String cheNamespace;
  private final HttpJsonRequestFactory httpJsonRequestFactory;
  private final String fabric8UserServiceEndpoint;
  private final LoadingCache<CacheKey, UserCheTenantData> tenantDataCache;

  @Inject
  public TenantDataProvider(
      HttpJsonRequestFactory httpJsonRequestFactory,
      @Named("che.fabric8.user_service.endpoint") String fabric8UserServiceEndpoint,
      @Named("che.infra.openshift.project") String cheNamespace,
      @Named("che.fabric8.standalone") boolean standalone) {
    this.fabric8UserServiceEndpoint = fabric8UserServiceEndpoint;
    this.cheNamespace = cheNamespace;
    this.standalone = standalone;
    this.httpJsonRequestFactory = httpJsonRequestFactory;
    this.tenantDataCache =
        CacheBuilder.newBuilder()
            .maximumSize(CONCURRENT_USERS)
            .expireAfterWrite(CACHE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build(CacheLoader.from(this::loadUserCheTenantData));
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
    checkSubject(subject);

    String keycloakToken = subject.getToken();
    if (keycloakToken == null) {
      throw new InfrastructureException("User tenant data is needed but there is no current user");
    }
    try {
      return tenantDataCache.get(new CacheKey(keycloakToken, namespaceType));
    } catch (ExecutionException e) {
      throw new InfrastructureException(
          "Exception during the user tenant data retrieval or parsing", e.getCause());
    }
  }

  private UserCheTenantData loadUserCheTenantData(CacheKey cacheKey) {

    if (standalone) {
      String namespaceType = cacheKey.namespaceType;
      String namespaceToUse;
      switch (namespaceType) {
        case "che":
          namespaceToUse = cheNamespace;
          break;
        default:
          namespaceToUse = "myproject";
      }
      return new UserCheTenantData(
          namespaceToUse, "https://kubernetes.default.svc", "dummy.prefix.unused", false);
    }

    String responseBody;
    try {
      responseBody = getResponseBody(fabric8UserServiceEndpoint, cacheKey.keycloakToken);
    } catch (ServerException
        | UnauthorizedException
        | ForbiddenException
        | NotFoundException
        | ConflictException
        | BadRequestException
        | IOException e) {
      throw new RuntimeException("Exception during the user tenant data retrieval", e);
    }
    try {
      final JsonArray namespaces =
          new JsonParser()
              .parse(responseBody)
              .getAsJsonObject()
              .get("data")
              .getAsJsonObject()
              .get("attributes")
              .getAsJsonObject()
              .get("namespaces")
              .getAsJsonArray();
      for (JsonElement e : namespaces) {
        JsonObject namespace = e.getAsJsonObject();
        if (cacheKey.namespaceType.equals(namespace.get("type").getAsString())) {
          String name = namespace.get("name").getAsString();
          String suffix = namespace.get("cluster-app-domain").getAsString();
          String cluster = namespace.get("cluster-url").getAsString();
          boolean clusterCapacityExhausted =
              namespace.get("cluster-capacity-exhausted").getAsBoolean();

          UserCheTenantData cheTenantData =
              new UserCheTenantData(name, cluster, suffix, clusterCapacityExhausted);
          UserCheTenantDataValidator.validate(cheTenantData);
          return cheTenantData;
        }
      }
    } catch (NullPointerException | IllegalStateException e) {
      throw new RuntimeException("Invalid response from Fabric8 user services:" + responseBody, e);
    }
    throw new RuntimeException(
        format("No namespace with type '%s' was found in the user tenant", cacheKey.namespaceType));
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

  private String getResponseBody(final String endpoint, final String keycloakToken)
      throws ServerException, UnauthorizedException, ForbiddenException, NotFoundException,
          ConflictException, BadRequestException, IOException {
    HttpJsonResponse response =
        httpJsonRequestFactory
            .fromUrl(endpoint)
            .setMethod("GET")
            .setAuthorizationHeader("Bearer " + keycloakToken)
            .request();
    return response.asString();
  }

  private static class CacheKey {

    private final String keycloakToken;
    private final String namespaceType;

    CacheKey(String keycloakToken, String namespaceType) {
      this.keycloakToken = keycloakToken;
      this.namespaceType = namespaceType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof CacheKey)) {
        return false;
      }
      CacheKey cacheKey = (CacheKey) o;
      return Objects.equals(keycloakToken, cacheKey.keycloakToken)
          && Objects.equals(namespaceType, cacheKey.namespaceType);
    }

    @Override
    public int hashCode() {
      return Objects.hash(keycloakToken, namespaceType);
    }
  }
}
