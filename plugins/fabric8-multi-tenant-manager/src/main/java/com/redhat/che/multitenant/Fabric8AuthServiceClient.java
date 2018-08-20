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

import com.google.common.io.CharStreams;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.jsonwebtoken.Jwt;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.lang.Pair;
import org.eclipse.che.dto.server.DtoFactory;
import org.eclipse.che.multiuser.keycloak.server.KeycloakServiceClient;
import org.eclipse.che.multiuser.keycloak.server.KeycloakSettings;
import org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants;
import org.eclipse.che.multiuser.keycloak.shared.dto.KeycloakErrorResponse;

@Singleton
public class Fabric8AuthServiceClient extends KeycloakServiceClient {

  private static final String LINKING_URL_SUFFIX = "/link";
  private KeycloakSettings keycloakSettings;
  private static final Gson gson =
      new GsonBuilder()
          .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
          .create();

  @Inject
  public Fabric8AuthServiceClient(KeycloakSettings keycloakSettings) {
    super(keycloakSettings);
    this.keycloakSettings = keycloakSettings;
  }

  @Override
  public String getAccountLinkingURL(
      @SuppressWarnings("rawtypes") Jwt token, String oauthProvider, String redirectAfterLogin) {
    // TODO: Better way of obtaining link url
    String linkingEndpoint =
        UriBuilder.fromUri(keycloakSettings.get().get(KeycloakConstants.TOKEN_ENDPOINT_SETTING))
            .path(LINKING_URL_SUFFIX)
            .queryParam("for", "https://github.com")
            .queryParam("redirect", redirectAfterLogin)
            .build()
            .toString();
    try {
      // TODO: require Authorization header in this request so have to do it here
      String redirectLocationJson = doRequest(linkingEndpoint, HttpMethod.GET, null);
      String redirectLocation =
          gson.<Map<String, String>>fromJson(redirectLocationJson, Map.class)
              .get("redirect_location");
      return redirectLocation;
    } catch (ServerException
        | ForbiddenException
        | NotFoundException
        | UnauthorizedException
        | BadRequestException
        | IOException e) {
      return null;
    }
  }

  public GithubToken getGithubToken()
      throws ServerException, ForbiddenException, NotFoundException, UnauthorizedException,
          BadRequestException, IOException {
    String githubEndpoint = keycloakSettings.get().get(KeycloakConstants.GITHUB_ENDPOINT_SETTING);
    String response = doRequest(githubEndpoint, HttpMethod.GET, null);
    return gson.fromJson(response, GithubToken.class);
  }

  // TODO Duplicated code from `KeycloakServiceClient`
  private String doRequest(String url, String method, List<Pair<String, ?>> parameters)
      throws IOException, ServerException, ForbiddenException, NotFoundException,
          UnauthorizedException, BadRequestException {
    final String authToken = EnvironmentContext.getCurrent().getSubject().getToken();
    final boolean hasQueryParams = parameters != null && !parameters.isEmpty();
    if (hasQueryParams) {
      final UriBuilder ub = UriBuilder.fromUri(url);
      for (Pair<String, ?> parameter : parameters) {
        ub.queryParam(parameter.first, parameter.second);
      }
      url = ub.build().toString();
    }
    final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setConnectTimeout(60000);
    conn.setReadTimeout(60000);

    try {
      conn.setRequestMethod(method);
      // drop a hint for server side that we want to receive application/json
      conn.addRequestProperty(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
      if (authToken != null) {
        conn.setRequestProperty(HttpHeaders.AUTHORIZATION, "bearer " + authToken);
      }
      final int responseCode = conn.getResponseCode();
      if ((responseCode / 100) != 2) {
        InputStream in = conn.getErrorStream();
        if (in == null) {
          in = conn.getInputStream();
        }
        final String str;
        try (Reader reader = new InputStreamReader(in)) {
          str = CharStreams.toString(reader);
        }
        final String contentType = conn.getContentType();
        if (contentType != null
            && (contentType.startsWith(MediaType.APPLICATION_JSON)
                || contentType.startsWith("application/vnd.api+json"))) {
          final KeycloakErrorResponse serviceError =
              DtoFactory.getInstance().createDtoFromJson(str, KeycloakErrorResponse.class);
          if (responseCode == Response.Status.FORBIDDEN.getStatusCode()) {
            throw new ForbiddenException(serviceError.getErrorMessage());
          } else if (responseCode == Response.Status.NOT_FOUND.getStatusCode()) {
            throw new NotFoundException(serviceError.getErrorMessage());
          } else if (responseCode == Response.Status.UNAUTHORIZED.getStatusCode()) {
            throw new UnauthorizedException(serviceError.getErrorMessage());
          } else if (responseCode == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
            throw new ServerException(serviceError.getErrorMessage());
          } else if (responseCode == Response.Status.BAD_REQUEST.getStatusCode()) {
            throw new BadRequestException(serviceError.getErrorMessage());
          }
          throw new ServerException(serviceError.getErrorMessage());
        }
        // Can't parse content as json or content has format other we expect for error.
        throw new IOException(
            String.format(
                "Failed access: %s, method: %s, response code: %d, message: %s",
                UriBuilder.fromUri(url).replaceQuery("token").build(), method, responseCode, str));
      }
      try (Reader reader = new InputStreamReader(conn.getInputStream())) {
        return CharStreams.toString(reader);
      }
    } finally {
      conn.disconnect();
    }
  }

  class GithubToken {
    private String accessToken;
    private String providerApiUrl;
    private String scope;
    private String tokenType;
    private String username;

    public String getAccessToken() {
      return accessToken;
    }

    public String getProviderApiUrl() {
      return providerApiUrl;
    }

    public String getScope() {
      return scope;
    }

    public String getTokenType() {
      return tokenType;
    }

    public String getUsername() {
      return username;
    }
  }
}
