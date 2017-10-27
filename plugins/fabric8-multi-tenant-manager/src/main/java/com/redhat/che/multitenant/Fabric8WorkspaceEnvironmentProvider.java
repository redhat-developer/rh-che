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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import java.io.IOException;
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
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.multiuser.keycloak.token.provider.service.KeycloakTokenProvider;
import org.eclipse.che.plugin.openshift.client.OpenshiftWorkspaceEnvironmentProvider;
import org.eclipse.che.plugin.openshift.client.exception.OpenShiftException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Fabric8WorkspaceEnvironmentProvider extends OpenshiftWorkspaceEnvironmentProvider {

  private static final Logger LOG =
      LoggerFactory.getLogger(Fabric8WorkspaceEnvironmentProvider.class);

  private static final int CACHE_TIMEOUT_MINUTES = 10;
  private static final int CONCURRENT_USERS = 500;

  private KeycloakTokenProvider keycloakTokenProvider;
  private HttpJsonRequestFactory httpJsonRequestFactory;

  LoadingCache<String, String> tokenCache;
  LoadingCache<String, UserCheTenantData> tenantDataCache;

  private boolean fabric8CheMultitenant;

  private String fabric8UserServiceEndpoint;

  public static class UserCheTenantData {
    private String namespace;
    private String clusterUrl;
    private String routePrefix;

    public UserCheTenantData(String namespace, String clusterUrl, String routePrefix) {
      this.namespace = namespace;
      this.clusterUrl = clusterUrl;
      this.routePrefix = routePrefix;
    }

    public String getNamespace() {
      return namespace;
    }

    public String getClusterUrl() {
      return clusterUrl;
    }

    public String getRouteBaseSuffix() {
      return routePrefix;
    }

    @Override
    public String toString() {
      return "{" + namespace + "," + clusterUrl + "," + routePrefix + "}";
    }
  }

  @Inject
  public Fabric8WorkspaceEnvironmentProvider(
      @Named("che.openshift.project") String openShiftCheProjectName,
      @Named("che.fabric8.multitenant") boolean fabric8CheMultitenant,
      @Named("che.fabric8.user_service.endpoint") String fabric8UserServiceEndpoint,
      KeycloakTokenProvider keycloakTokenProvider,
      HttpJsonRequestFactory httpJsonRequestFactory) {
    super(openShiftCheProjectName);
    LOG.info("fabric8CheMultitenant = {}", fabric8CheMultitenant);
    LOG.info("fabric8UserServiceEndpoint = {}", fabric8UserServiceEndpoint);
    this.fabric8CheMultitenant = fabric8CheMultitenant;
    this.fabric8UserServiceEndpoint = fabric8UserServiceEndpoint;
    this.keycloakTokenProvider = keycloakTokenProvider;
    this.httpJsonRequestFactory = httpJsonRequestFactory;

    this.tokenCache =
        CacheBuilder.newBuilder()
            .maximumSize(CONCURRENT_USERS)
            .expireAfterWrite(CACHE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build(CacheLoader.from(this::loadOpenShiftTokenForUser));
    this.tenantDataCache =
        CacheBuilder.newBuilder()
            .maximumSize(CONCURRENT_USERS)
            .expireAfterWrite(CACHE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build(CacheLoader.from(this::loadUserCheTenantData));
  }

  @Override
  public Config getWorkspacesOpenshiftConfig() throws OpenShiftException {
    if (!fabric8CheMultitenant) {
      return super.getWorkspacesOpenshiftConfig();
    }

    String osoToken = getOpenShiftTokenForUser();
    if (osoToken == null) {
      throw new OpenShiftException(
          "OSO token is null => Connecting to Openshift with default config");
    }

    UserCheTenantData cheTenantData;
    cheTenantData = getUserCheTenantData();
    if (cheTenantData == null) {
      throw new OpenShiftException(
          "User tenant data not found => Connecting to Openshift with default config");
    }

    return new ConfigBuilder()
        .withMasterUrl(cheTenantData.getClusterUrl())
        .withOauthToken(getOpenShiftTokenForUser())
        .withNamespace(cheTenantData.getNamespace())
        .withTrustCerts(true)
        .build();
  }

  private @Nullable String loadOpenShiftTokenForUser(String keycloakToken) {
    try {
      return keycloakTokenProvider.obtainOsoToken("Bearer " + keycloakToken);
    } catch (ServerException
        | UnauthorizedException
        | ForbiddenException
        | NotFoundException
        | ConflictException
        | BadRequestException
        | IOException e) {
      throw new RuntimeException(
          "Cound not retrieve OSO token from Keycloak token: " + keycloakToken, e);
    }
  }

  public String getOpenShiftTokenForUser() throws OpenShiftException {
    String keycloakToken = EnvironmentContext.getCurrent().getSubject().getToken();
    if (keycloakToken == null) {
      throw new OpenShiftException("User Openshift token is needed but there is no current user");
    }
    try {
      return tokenCache.get(keycloakToken);
    } catch (ExecutionException e) {
      throw new OpenShiftException(
          "Cound not retrieve OSO token from Keycloak token: " + keycloakToken, e.getCause());
    }
  }

  private UserCheTenantData loadUserCheTenantData(String keycloakToken) {
    LOG.info("Retrieving user Che tenant data");

    String responseBody;
    try {
      responseBody = getResponseBody(fabric8UserServiceEndpoint, keycloakToken);
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
        if ("che".equals(namespace.get("type").getAsString())) {
          String clusterUrl = namespace.get("cluster-url").getAsString();
          String name = namespace.get("name").getAsString();

          // TODO: When the routing prefix will be added in the userServices, we will set it here.
          UserCheTenantData cheTenantData = new UserCheTenantData(name, clusterUrl, null);
          LOG.info("cheTenantData = {}", cheTenantData);
          return cheTenantData;
        }
      }
    } catch (NullPointerException | IllegalStateException e) {
      throw new RuntimeException("Invalid response from Fabric8 user services:" + responseBody, e);
    }
    throw new RuntimeException("No che namespace was found in the user tenant");
  }

  public UserCheTenantData getUserCheTenantData() throws OpenShiftException {
    String keycloakToken = EnvironmentContext.getCurrent().getSubject().getToken();
    if (keycloakToken == null) {
      throw new OpenShiftException("User tenant data is needed but there is no current user");
    }
    try {
      return tenantDataCache.get(keycloakToken);
    } catch (ExecutionException e) {
      throw new OpenShiftException(
          "Exception during the user tenant data retrieval or parsing", e.getCause());
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

  @Override
  public String getWorkspacesOpenshiftNamespace() throws OpenShiftException {
    if (!fabric8CheMultitenant) {
      return super.getWorkspacesOpenshiftNamespace();
    }

    UserCheTenantData cheTenantData = getUserCheTenantData();
    if (cheTenantData == null) {
      throw new OpenShiftException("User tenant data not found !");
    }
    return cheTenantData.getNamespace();
  }

  @Override
  public Boolean areWorkspacesExternal() {
    return fabric8CheMultitenant;
  }
}
