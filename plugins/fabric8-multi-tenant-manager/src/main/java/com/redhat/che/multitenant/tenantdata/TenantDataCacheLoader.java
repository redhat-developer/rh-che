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

import static java.lang.String.format;

import com.google.common.cache.CacheLoader;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.redhat.che.multitenant.tenantdata.UserServicesJsonResponse.Namespace;
import java.io.IOException;
import java.util.List;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.core.ValidationException;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.core.rest.HttpJsonResponse;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TenantDataCacheLoader extends CacheLoader<TenantDataCacheKey, UserCheTenantData> {
  private static final Logger LOG = LoggerFactory.getLogger(TenantDataCacheLoader.class);
  private static final String API_USER_SERVICES_PATH = "/api/user/services";
  private final HttpJsonRequestFactory httpJsonRequestFactory;
  private final String fabric8UserServiceEndpoint;

  @Inject
  TenantDataCacheLoader(
      HttpJsonRequestFactory httpJsonRequestFactory,
      @Named("che.fabric8.auth.endpoint") String fabric8AuthEndpoint) {
    this.fabric8UserServiceEndpoint = fabric8AuthEndpoint + API_USER_SERVICES_PATH;
    this.httpJsonRequestFactory = httpJsonRequestFactory;
  }

  @Override
  public UserCheTenantData load(TenantDataCacheKey cacheKey) throws InfrastructureException {
    String responseBody;
    try {
      responseBody = getResponseBody(fabric8UserServiceEndpoint, cacheKey.getKeycloakToken());
    } catch (ApiException | IOException e) {
      LOG.error(e.getMessage(), e);
      throw new InfrastructureException("Exception during the user tenant data retrieval", e);
    }
    try {
      final Gson gson = new Gson();
      final UserServicesJsonResponse userServicesData =
          gson.fromJson(responseBody, UserServicesJsonResponse.class);
      List<Namespace> namespaces = userServicesData.getNamespaces();
      for (Namespace ns : namespaces) {
        if (cacheKey.getNamespaceType().equals(ns.getType())) {
          UserCheTenantData cheTenantData =
              new UserCheTenantData(
                  ns.getName(),
                  ns.getClusterUrl(),
                  ns.getClusterAppDomain(),
                  ns.isClusterCapacityExhausted());
          UserCheTenantDataValidator.validate(cheTenantData);
          return cheTenantData;
        }
      }
    } catch (ValidationException | JsonSyntaxException | NullPointerException e) {
      throw new InfrastructureException(
          "Invalid response from Fabric8 user services:" + responseBody, e);
    }
    throw new InfrastructureException(
        format(
            "No namespace with type '%s' was found in the user tenant",
            cacheKey.getNamespaceType()));
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
}
