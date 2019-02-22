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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.io.IOException;
import org.eclipse.che.api.core.rest.HttpJsonRequest;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.core.rest.HttpJsonResponse;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class TenantDataCacheLoaderTest {
  private static final String RESPONSE_FORMAT =
      "{\"data\":{\"attributes\":{\"created-at\":\"createdAt\",\"namespaces\":[{\"cluster-app-domain\": %s,\"cluster-capacity-exhausted\": %b,\"cluster-url\": %s,\"name\": %s,\"type\": %s}]},\"id\":\"userid\",\"type\":\"userservices\"}}";
  // Does not contain one of the fields (cluster-url) from above, will result in deserialization to
  // null
  private static final String BAD_RESPONSE_FORMAT =
      "{\"data\":{\"attributes\":{\"created-at\":\"createdAt\",\"namespaces\":[{\"cluster-app-domain\": %s,\"cluster-capacity-exhausted\": %b,\"name\": %s,\"type\": %s}]},\"id\":\"userid\",\"type\":\"userservices\"}}";

  private static final String NAMESPACE = "test-namespace";
  private static final String ENDPOINT = "test-endpoint";
  private static final String CLUSTER_URL = "test-url";
  private static final String ROUTE_PREFIX = "test-route-prefix";
  private static final String NAMESPACE_TYPE = "che";
  private static final String BAD_NAMESPACE_TYPE = "not-che";

  @Mock private HttpJsonRequestFactory httpJsonRequestFactory;
  @Mock private HttpJsonResponse httpJsonResponse;
  @Mock private HttpJsonRequest httpJsonRequest;

  private TenantDataCacheLoader cacheLoader;

  @BeforeMethod
  public void setUp() throws Exception {
    when(httpJsonRequestFactory.fromUrl(any(String.class))).thenReturn(httpJsonRequest);
    when(httpJsonRequest.setMethod(any(String.class))).thenReturn(httpJsonRequest);
    when(httpJsonRequest.setAuthorizationHeader(any(String.class))).thenReturn(httpJsonRequest);
    when(httpJsonRequest.request()).thenReturn(httpJsonResponse);

    cacheLoader = new TenantDataCacheLoader(httpJsonRequestFactory, ENDPOINT);
  }

  @Test
  public void shouldParseJsonAndReturnTenantData() throws Exception {
    // Given
    String jsonResponse =
        generateResponse(ROUTE_PREFIX, CLUSTER_URL, NAMESPACE, NAMESPACE_TYPE, false);
    when(httpJsonResponse.asString()).thenReturn(jsonResponse);
    TenantDataCacheKey cacheKey = new TenantDataCacheKey("token", "che");

    // When
    UserCheTenantData data = cacheLoader.load(cacheKey);

    // Then
    assertEquals(data.getRouteBaseSuffix(), ROUTE_PREFIX);
    assertEquals(data.getClusterUrl(), CLUSTER_URL);
    assertEquals(data.getNamespace(), NAMESPACE);
    assertEquals(data.isClusterCapacityExhausted(), false);
  }

  @Test(
      expectedExceptions = InfrastructureException.class,
      expectedExceptionsMessageRegExp = ".*retrieval.*")
  public void shouldThrowInfrastructureExceptionWhenFailToGetResponse() throws Exception {
    // Given
    when(httpJsonRequest.request()).thenThrow(new IOException());
    TenantDataCacheKey cacheKey = new TenantDataCacheKey("token", "che");

    // When
    cacheLoader.load(cacheKey);
  }

  @Test(
      expectedExceptions = InfrastructureException.class,
      expectedExceptionsMessageRegExp = "No namespace with type 'che'.*")
  public void shouldThrowInfrastructureExceptionWhenNoNamespaceMatchesCacheKey() throws Exception {
    // Given
    String jsonResponse =
        generateResponse(ROUTE_PREFIX, CLUSTER_URL, NAMESPACE, BAD_NAMESPACE_TYPE, false);
    when(httpJsonResponse.asString()).thenReturn(jsonResponse);
    TenantDataCacheKey cacheKey = new TenantDataCacheKey("token", NAMESPACE_TYPE);

    // When
    cacheLoader.load(cacheKey);
  }

  @Test(
      expectedExceptions = InfrastructureException.class,
      expectedExceptionsMessageRegExp = "Invalid response.*")
  public void shouldThrowInfrastructureExceptionWhenResponseFailsToValidate() throws Exception {
    // Given
    String jsonResponse = generateBadResponse(ROUTE_PREFIX, NAMESPACE, NAMESPACE_TYPE, false);
    when(httpJsonResponse.asString()).thenReturn(jsonResponse);
    TenantDataCacheKey cacheKey = new TenantDataCacheKey("token", "che");

    // When
    cacheLoader.load(cacheKey);
  }

  @Test(
      expectedExceptions = InfrastructureException.class,
      expectedExceptionsMessageRegExp = "Invalid response.*")
  public void shouldThrowInfrastructureExceptionWhenResponseParsesToNull() throws Exception {
    // Given
    when(httpJsonResponse.asString()).thenReturn("{}"); // Should parse to null
    TenantDataCacheKey cacheKey = new TenantDataCacheKey("token", "che");

    // When
    cacheLoader.load(cacheKey);
  }

  private String generateResponse(
      String routePrefix,
      String clusterUrl,
      String namespaceName,
      String type,
      boolean capacityExhausted) {
    return String.format(
        RESPONSE_FORMAT, routePrefix, capacityExhausted, clusterUrl, namespaceName, type);
  }

  private String generateBadResponse(
      String routePrefix, String namespaceName, String type, boolean capacityExhausted) {
    return String.format(BAD_RESPONSE_FORMAT, routePrefix, capacityExhausted, namespaceName, type);
  }
}
