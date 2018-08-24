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

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.redhat.che.multitenant.Fabric8AuthServiceClient.GithubToken;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.dto.server.DtoFactory;
import org.eclipse.che.multiuser.keycloak.server.oauth2.DelegatedOAuthAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author Angel Misevski (amisevsk@redhat.com) */
public class Fabric8DelegatedOAuthAPI extends DelegatedOAuthAPI {

  private static final Logger LOG = LoggerFactory.getLogger(Fabric8DelegatedOAuthAPI.class);
  private final Fabric8AuthServiceClient authServiceClient;

  @Inject
  public Fabric8DelegatedOAuthAPI(Fabric8AuthServiceClient authServiceClient) {
    super(authServiceClient);
    this.authServiceClient = authServiceClient;
  }

  @Override
  public Response authenticate(
      UriInfo uriInfo,
      String oauthProvider,
      List<String> scopes,
      String redirectAfterLogin,
      HttpServletRequest request)
      throws BadRequestException {
    String accountLinkingUrl =
        authServiceClient.getAccountLinkingURL(null, oauthProvider, redirectAfterLogin);
    if (Strings.isNullOrEmpty(accountLinkingUrl)) {
      LOG.error("Failed to get GitHub account linking URL");
      return Response.status(500).build();
    }
    return Response.temporaryRedirect(URI.create(accountLinkingUrl)).build();
  }

  @Override
  public OAuthToken getToken(String oauthProvider)
      throws ForbiddenException, BadRequestException, NotFoundException, ServerException,
          UnauthorizedException {
    try {
      GithubToken token = authServiceClient.getGithubToken();
      return DtoFactory.newDto(OAuthToken.class)
          .withToken(token.getAccessToken())
          .withScope(token.getScope());
    } catch (IOException e) {
      throw new ServerException(e.getMessage());
    }
  }
}
