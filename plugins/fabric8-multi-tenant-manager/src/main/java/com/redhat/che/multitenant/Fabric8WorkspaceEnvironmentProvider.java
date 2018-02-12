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
import com.redhat.che.multitenant.multicluster.MultiClusterOpenShiftProxy;
import com.redhat.che.multitenant.toggle.CheServiceAccountTokenToggle;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
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
import org.eclipse.che.commons.subject.Subject;
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

  private MultiClusterOpenShiftProxy multiClusterOpenShiftProxy;
  private CheServiceAccountTokenToggle cheServiceAccountTokenToggle;
  private HttpJsonRequestFactory httpJsonRequestFactory;

  private LoadingCache<String, UserCheTenantData> tenantDataCache;

  private boolean fabric8CheMultitenant;

  private String fabric8UserServiceEndpoint;

  private String cheToken;

  @Inject
  public Fabric8WorkspaceEnvironmentProvider(
      @Named("che.openshift.project") String openShiftCheProjectName,
      @Named("che.fabric8.multitenant") boolean fabric8CheMultitenant,
      @Named("che.fabric8.user_service.endpoint") String fabric8UserServiceEndpoint,
      MultiClusterOpenShiftProxy multiClusterOpenShiftProxy,
      CheServiceAccountTokenToggle cheServiceAccountTokenToggle,
      HttpJsonRequestFactory httpJsonRequestFactory) {
    super(openShiftCheProjectName);
    LOG.info("fabric8CheMultitenant = {}", fabric8CheMultitenant);
    LOG.info("fabric8UserServiceEndpoint = {}", fabric8UserServiceEndpoint);
    this.fabric8CheMultitenant = fabric8CheMultitenant;
    this.fabric8UserServiceEndpoint = fabric8UserServiceEndpoint;
    this.multiClusterOpenShiftProxy = multiClusterOpenShiftProxy;
    this.cheServiceAccountTokenToggle = cheServiceAccountTokenToggle;
    this.httpJsonRequestFactory = httpJsonRequestFactory;

    this.tenantDataCache =
        CacheBuilder.newBuilder()
            .maximumSize(CONCURRENT_USERS)
            .expireAfterWrite(CACHE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build(CacheLoader.from(this::loadUserCheTenantData));
  }

  @Inject
  private void setServiceAccountToken(
      @Nullable @Named("che.openshift.service_account.id") String serviceAccId,
      @Nullable @Named("che.openshift.service_account.secret") String serviceAccSecret,
      @Nullable @Named("che.fabric8.auth.endpoint") String authEndpoint) {

    if (serviceAccId == null || serviceAccId.isEmpty()) {
      return;
    }
    OkHttpClient client = new OkHttpClient();
    RequestBody requestBody =
        new FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", serviceAccId)
            .add("client_secret", serviceAccSecret)
            .build();

    Request request =
        new Request.Builder().url(authEndpoint + "/api/token").post(requestBody).build();
    try {
      Response response = client.newCall(request).execute();
      cheToken =
          new JsonParser()
              .parse(response.body().string())
              .getAsJsonObject()
              .get("access_token")
              .getAsString();

      LOG.info("Che Service account token has been successfully retrieved");
    } catch (IOException e) {
      throw new RuntimeException(
          "Service account token retrieving failed. Error: " + e.getMessage(), e);
    }
  }

  private void checkSubject(Subject subject) throws OpenShiftException {
    if (subject == null) {
      throw new OpenShiftException("No Subject is found to perform this action");
    }
    if (subject == Subject.ANONYMOUS) {
      throw new OpenShiftException(
          "The anonymous subject is used, and won't be able to perform this action");
    }
  }

  @Override
  public Config getDefaultOpenshiftConfig() {
    return new ConfigBuilder().withTrustCerts(true).build();
  }

  @Override
  public Config getWorkspacesOpenshiftConfig(Subject subject) throws OpenShiftException {
    if (!fabric8CheMultitenant) {
      return super.getWorkspacesOpenshiftConfig(subject);
    }

    checkSubject(subject);

    UserCheTenantData cheTenantData;
    cheTenantData = getUserCheTenantData(subject);
    if (cheTenantData == null) {
      throw new OpenShiftException(
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
        .withOauthToken(subject.getToken())
        .withNamespace(cheTenantData.getNamespace())
        .withTrustCerts(true)
        .build();
  }

  private String getUserDescription(Subject subject) {
    return subject.getUserName() + "(" + subject.getUserId() + ")";
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

  public UserCheTenantData getUserCheTenantData(Subject subject) throws OpenShiftException {
    checkSubject(subject);

    String keycloakToken = subject.getToken();
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

  public UserCheTenantData getUserCheTenantData() throws OpenShiftException {
    return getUserCheTenantData(EnvironmentContext.getCurrent().getSubject());
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
  public String getWorkspacesOpenshiftNamespace(Subject subject) throws OpenShiftException {
    if (!fabric8CheMultitenant) {
      return super.getWorkspacesOpenshiftNamespace(subject);
    }

    checkSubject(subject);

    UserCheTenantData cheTenantData = getUserCheTenantData(subject);
    if (cheTenantData == null) {
      throw new OpenShiftException(
          "User tenant data not found for user: " + getUserDescription(subject));
    }
    return cheTenantData.getNamespace();
  }

  @Override
  public Boolean areWorkspacesExternal() {
    return fabric8CheMultitenant;
  }
}
