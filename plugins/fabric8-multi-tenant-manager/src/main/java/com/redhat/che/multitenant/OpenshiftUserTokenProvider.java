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

import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.AUTH_SERVER_URL_SETTING;
import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.OIDC_PROVIDER_SETTING;
import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.REALM_SETTING;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.subject.Subject;

/**
 * Retrieves Openshift user token by keycloak token from {@link Subject}.
 *
 * @author Oleksandr Garagatyi
 */
@Singleton
public class OpenshiftUserTokenProvider {

  private static final int CACHE_TIMEOUT_MINUTES = 10;
  private static final int CONCURRENT_USERS = 500;

  private final String tokenEndpoint;
  private final OkHttpClient httpClient;
  private final LoadingCache<String, String> tokenCache;

  @Inject
  public OpenshiftUserTokenProvider(
      @Nullable @Named(OIDC_PROVIDER_SETTING) String oidcProvider,
      @Nullable @Named(AUTH_SERVER_URL_SETTING) String keycloakServerURL,
      @Nullable @Named(REALM_SETTING) String keycloakRealm,
      OkHttpClient httpClient,
      @Named("che.fabric8.standalone") boolean standalone) {

    if (standalone) {
      // When RhChe is used in standalone mode, it uses the dedicated Keycloak as
      // the Che authentication provider

      if (keycloakServerURL == null) {
        throw new RuntimeException("The 'AUTH_SERVER_URL_SETTING' property should be set");
      }
      tokenEndpoint = keycloakServerURL + "/realms/" + keycloakRealm + "/broker/openshift-v3/token";
    } else {
      // When RhChe is used in OSIO mode (along with the oter fabric8 services, it uses the
      // alternate
      // fabric8_auth OIDC provider as the Che authentication provider.

      if (oidcProvider == null) {
        throw new RuntimeException("The 'OIDC_PROVIDER_SETTING' property should be set");
      }
      tokenEndpoint = oidcProvider + "/token?for=openshift";
    }
    this.httpClient = httpClient;
    this.tokenCache =
        CacheBuilder.newBuilder()
            .maximumSize(CONCURRENT_USERS)
            .expireAfterWrite(CACHE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build(CacheLoader.from(this::getOsToken));
  }

  /**
   * Returns Openshift token corresponding to a keycloak token retrieved from provided {@link
   * Subject}
   *
   * @param subject subject with user's keycloak token
   * @return Openshift user token
   * @throws InfrastructureException when there is no keycloak token in subject or OSO token
   *     retrieval failed
   */
  public String getToken(Subject subject) throws InfrastructureException {
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

  private void checkSubject(Subject subject) throws InfrastructureException {
    if (subject == null) {
      throw new InfrastructureException("No Subject is found to perform this action");
    }
    if (subject == Subject.ANONYMOUS) {
      throw new InfrastructureException(
          "The anonymous subject is used, and won't be able to perform this action");
    }
  }

  private String getUserDescription(Subject subject) {
    return subject.getUserName() + "(" + subject.getUserId() + ")";
  }

  private String getOsToken(final String keycloakToken) throws RuntimeException {
    Request request =
        new Request.Builder()
            .url(tokenEndpoint)
            .get()
            .header("Authorization", "Bearer " + keycloakToken)
            .build();
    try (Response response = httpClient.newCall(request).execute()) {
      // Ignore IDE warning:
      // body is not null after call of execute() according to javadocs of method body()
      String body = response.body().string();

      if (!response.isSuccessful()) {
        throw new RuntimeException(
            "Could not retrieve OSO token of the user. Response from auth service: " + body);
      }

      return new JsonParser().parse(body).getAsJsonObject().get("access_token").getAsString();
    } catch (IOException e) {
      throw new RuntimeException(
          "Could not retrieve OSO token from of the user. Error: " + e.getMessage());
    }
  }
}
