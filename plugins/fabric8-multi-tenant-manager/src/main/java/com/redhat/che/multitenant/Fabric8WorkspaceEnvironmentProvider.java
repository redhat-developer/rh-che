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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.redhat.che.multitenant.multicluster.MultiClusterOpenShiftProxy;
import com.redhat.che.multitenant.toggle.CheServiceAccountTokenToggle;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.core.rest.HttpJsonResponse;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.inject.ConfigurationException;
import org.eclipse.che.multiuser.keycloak.token.provider.service.KeycloakTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Fabric8WorkspaceEnvironmentProvider {

  private static final Logger LOG =
      LoggerFactory.getLogger(Fabric8WorkspaceEnvironmentProvider.class);

  private static final int CACHE_TIMEOUT_MINUTES = 10;
  private static final int CONCURRENT_USERS = 500;

  private MultiClusterOpenShiftProxy multiClusterOpenShiftProxy;
  private CheServiceAccountTokenToggle cheServiceAccountTokenToggle;
  private final KeycloakTokenProvider keycloakTokenProvider;
  private final HttpJsonRequestFactory httpJsonRequestFactory;

  private final LoadingCache<String, String> tokenCache;
  private final LoadingCache<String, UserCheTenantData> tenantDataCache;

  private final String fabric8UserServiceEndpoint;
  private final boolean fabric8CheMultitenant;
  private final String projectName;

  @Inject
  public Fabric8WorkspaceEnvironmentProvider(
      @Named("che.fabric8.multitenant") boolean fabric8CheMultitenant,
      @Named("che.fabric8.user_service.endpoint") String fabric8UserServiceEndpoint,
      MultiClusterOpenShiftProxy multiClusterOpenShiftProxy,
      CheServiceAccountTokenToggle cheServiceAccountTokenToggle,
      @Nullable @Named("che.infra.openshift.project") String projectName,
      KeycloakTokenProvider keycloakTokenProvider,
      HttpJsonRequestFactory httpJsonRequestFactory) {
    LOG.info("fabric8CheMultitenant = {}", fabric8CheMultitenant);
    LOG.info("fabric8UserServiceEndpoint = {}", fabric8UserServiceEndpoint);
    if (!fabric8CheMultitenant) {
      throw new ConfigurationException(
          "Fabric8 Che Multitetant is disabled. "
              + "che.infra.openshift.project must be configured with non null value");
    }
    this.fabric8CheMultitenant = fabric8CheMultitenant;
    this.fabric8UserServiceEndpoint = fabric8UserServiceEndpoint;
    this.multiClusterOpenShiftProxy = multiClusterOpenShiftProxy;
    this.cheServiceAccountTokenToggle = cheServiceAccountTokenToggle;
    this.projectName = projectName;
    this.keycloakTokenProvider = keycloakTokenProvider;
    this.httpJsonRequestFactory = httpJsonRequestFactory;

    this.tenantDataCache =
        CacheBuilder.newBuilder()
            .maximumSize(CONCURRENT_USERS)
            .expireAfterWrite(CACHE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build(CacheLoader.from(this::loadUserCheTenantData));
  }

  public Config getDefaultOpenshiftConfig() {
    return new ConfigBuilder().withTrustCerts(true).build();
  }

  public Config getWorkspacesOpenshiftConfig(Subject subject) throws InfrastructureException {
    if (!fabric8CheMultitenant) {
      return new OpenShiftConfigBuilder().build();
    }
    checkSubject(subject);

    String keycloakToken = subject.getToken();

    UserCheTenantData cheTenantData;
    cheTenantData = getUserCheTenantData(subject);
    if (cheTenantData == null) {
      throw new InfrastructureException(
          "User tenant data not found for user: " + getUserDescription(subject));
    }

    String osoProxyUrl = multiClusterOpenShiftProxy.getUrl();
    LOG.info("OSO proxy URL - {}", osoProxyUrl);
    String userId = subject.getUserId();

    if (cheServiceAccountTokenToggle.useCheServiceAccountToken(userId)) {
      LOG.info("Using Che SA token for '{}'", userId);
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
    if (!fabric8CheMultitenant) {
      return projectName;
    }
    checkSubject(subject);

    UserCheTenantData cheTenantData = getUserCheTenantData(subject);
    if (cheTenantData == null) {
      throw new InfrastructureException(
          "User tenant data not found for user: " + getUserDescription(subject));
    }
    return cheTenantData.getNamespace();
  }

  private String getOpenShiftTokenForUser(Subject subject) throws InfrastructureException {
    checkSubject(subject);

    String keycloakToken = subject.getToken();
    if (keycloakToken == null) {
      throw new InfrastructureException(
          "User Openshift token is needed but cannot be retrieved since there is no Keycloak token for user: "
              + getUserDescription(subject));
    }
    try {
      return tokenCache.get(keycloakToken);
    } catch (ExecutionException e) {
      throw new InfrastructureException(
          "Could not retrieve OSO token from Keycloak token for user: "
              + getUserDescription(subject),
          e.getCause());
    }
  }

  private UserCheTenantData loadUserCheTenantData(String keycloakToken) {
    LOG.info("Retrieving user Che tenant data");

    String responseBody;
    try {
      responseBody = getResponseBody(fabric8UserServiceEndpoint, keycloakToken);
    } catch (ApiException | IOException e) {
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
          String name = namespace.get("name").getAsString();
          String suffix = namespace.get("cluster-app-domain").getAsString();
          String osoProxyUrl = multiClusterOpenShiftProxy.getUrl();

          UserCheTenantData cheTenantData = new UserCheTenantData(name, osoProxyUrl, suffix);
          UserCheTenantDataValidator.validate(cheTenantData);
          LOG.info("cheTenantData = {}", cheTenantData);
          return cheTenantData;
        }
      }
    } catch (NullPointerException | IllegalStateException e) {
      throw new RuntimeException("Invalid response from Fabric8 user services:" + responseBody, e);
    }
    throw new RuntimeException("No che namespace was found in the user tenant");
  }

  private UserCheTenantData getUserCheTenantData(Subject subject) throws InfrastructureException {
    checkSubject(subject);

    String keycloakToken = subject.getToken();
    if (keycloakToken == null) {
      throw new InfrastructureException("User tenant data is needed but there is no current user");
    }
    try {
      return tenantDataCache.get(keycloakToken);
    } catch (ExecutionException e) {
      throw new InfrastructureException(
          "Exception during the user tenant data retrieval or parsing", e.getCause());
    }
  }

  private String getResponseBody(final String endpoint, final String keycloakToken)
      throws ApiException, IOException {
    HttpJsonResponse response =
        httpJsonRequestFactory
            .fromUrl(endpoint)
            .setMethod("GET")
            .setAuthorizationHeader("Bearer " + keycloakToken)
            .request();
    return response.asString();
  }

  private String getUserDescription(Subject subject) {
    return subject.getUserName() + "(" + subject.getUserId() + ")";
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

  private static class UserCheTenantData {
    private String namespace;
    private String clusterUrl;

    UserCheTenantData(String namespace, String clusterUrl) {
      this.namespace = namespace;
      this.clusterUrl = clusterUrl;
    }

    String getNamespace() {
      return namespace;
    }

    String getClusterUrl() {
      return clusterUrl;
    }

    @Override
    public String toString() {
      return "{" + namespace + "," + clusterUrl + "}";
    }
  }
}
