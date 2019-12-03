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

import com.google.common.io.CharStreams;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.name.Named;
import io.jsonwebtoken.JwtParser;
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
import org.eclipse.che.multiuser.keycloak.shared.dto.KeycloakErrorResponse;

/**
 * Abstraction of fabric8 auth server, allowing for account linking and obtaining github tokens.
 *
 * @author Angel Misevski (amisevsk@redhat.com)
 */
@Singleton
public class Fabric8AuthServiceClient extends KeycloakServiceClient {

  private static final String GITHUB_LINK_API_PATH = "/api/token/link?for=https://github.com";
  private static final String GITHUB_TOKEN_API_PATH = "/api/token?for=https://github.com";
  private static final Gson gson =
      new GsonBuilder()
          .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
          .create();

  private final String githubTokenEndpoint;
  private final String githubLinkEndpoint;

  @Inject
  public Fabric8AuthServiceClient(
      @Named("che.fabric8.auth.endpoint") String baseAuthUrl,
      KeycloakSettings keycloakSettings,
      JwtParser jwtParser) {
    super(keycloakSettings, jwtParser);
    this.githubTokenEndpoint = baseAuthUrl + GITHUB_TOKEN_API_PATH;
    this.githubLinkEndpoint = baseAuthUrl + GITHUB_LINK_API_PATH;
  }

  /**
   * Return account linking url for user. Note that this does not return the fabric8 auth linking
   * URL, but instead makes a request to that url and returns the redirect obtained from there. This
   * is because requests to fabric8 auth need the Authorization header, and a user's browser will
   * not include this header by default, so returning the direct URL does not work.
   */
  @Override
  public String getAccountLinkingURL(
      String token, String oauthProvider, String redirectAfterLogin) {
    String linkingEndpoint =
        UriBuilder.fromUri(githubLinkEndpoint)
            .queryParam("redirect", redirectAfterLogin)
            .build()
            .toString();
    try {
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

  /** Return user's {@link GithubToken} from the fabric8 auth github endpoint. */
  public GithubToken getGithubToken()
      throws ServerException, ForbiddenException, NotFoundException, UnauthorizedException,
          BadRequestException, IOException {
    String response = doRequest(githubTokenEndpoint, HttpMethod.GET, null);
    return gson.fromJson(response, GithubToken.class);
  }

  /** Note this method is duplicated from {@link KeycloakServiceClient} */
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

  /** Class to allow parsing json response from auth server for github token */
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
