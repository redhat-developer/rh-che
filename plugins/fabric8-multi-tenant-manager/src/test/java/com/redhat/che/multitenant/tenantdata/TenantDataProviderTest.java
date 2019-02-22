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

import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class TenantDataProviderTest {

  private static final String NAMESPACE_TYPE = "che";
  private static final String NAMESPACE = "test-namespace";

  @Mock private TenantDataCacheLoader cacheLoader;
  @Mock private SubjectImpl subject;

  private TenantDataProvider tenantDataProvider;
  private TenantDataProvider standaloneDataProvider;

  @BeforeMethod
  public void setUp() {
    when(subject.getToken()).thenReturn("token");
    tenantDataProvider = new TenantDataProvider(cacheLoader, NAMESPACE, false);
    standaloneDataProvider = new TenantDataProvider(cacheLoader, NAMESPACE, true);
  }

  @Test
  public void shouldReturnTenantData() throws Exception {
    // Given
    UserCheTenantData expectedTenantData =
        new UserCheTenantData("namespace", "clusterUrl", "routePrefix", false);
    when(cacheLoader.load(any())).thenReturn(expectedTenantData);

    // When
    UserCheTenantData actualTenantData =
        tenantDataProvider.getUserCheTenantData(subject, NAMESPACE_TYPE);

    // Then
    assertEquals(actualTenantData.getClusterUrl(), expectedTenantData.getClusterUrl());
    assertEquals(actualTenantData.getNamespace(), expectedTenantData.getNamespace());
    assertEquals(actualTenantData.getRouteBaseSuffix(), expectedTenantData.getRouteBaseSuffix());
    assertEquals(
        actualTenantData.isClusterCapacityExhausted(),
        expectedTenantData.isClusterCapacityExhausted());
  }

  @Test
  public void standaloneShouldReturnDefaultDataWithCheNamespaceType() throws Exception {
    // When
    UserCheTenantData data = standaloneDataProvider.getUserCheTenantData(subject, NAMESPACE_TYPE);

    // Then
    assertEquals(data.getClusterUrl(), "https://kubernetes.default.svc");
    assertEquals(data.getNamespace(), NAMESPACE);
    assertEquals(data.isClusterCapacityExhausted(), false);
  }

  @Test
  public void standaloneShouldReturnDefaultDataWithArbitraryNamespaceType() throws Exception {
    // When
    UserCheTenantData data = standaloneDataProvider.getUserCheTenantData(subject, "not-che");

    // Then
    assertEquals(data.getClusterUrl(), "https://kubernetes.default.svc");
    assertEquals(data.getNamespace(), "myproject");
    assertEquals(data.isClusterCapacityExhausted(), false);
  }

  @Test(expectedExceptions = InfrastructureException.class)
  public void shouldThrowInfrastructureExceptionWhenUnableToLoadTenantData() throws Exception {
    // Given
    when(cacheLoader.load(any())).thenThrow(new InfrastructureException("test-failure"));

    // When
    tenantDataProvider.getUserCheTenantData(subject, NAMESPACE_TYPE);
  }

  @Test(
      expectedExceptions = InfrastructureException.class,
      expectedExceptionsMessageRegExp = ".*anonymous subject.*")
  public void shouldThrowInfrastructureExceptionWhenAnonymousSubjectUsed() throws Exception {
    // When
    tenantDataProvider.getUserCheTenantData(Subject.ANONYMOUS, NAMESPACE_TYPE);
  }

  @Test(expectedExceptions = InfrastructureException.class)
  public void shouldThrowInfrastructureExceptionWhenNullSubject() throws Exception {
    // When
    tenantDataProvider.getUserCheTenantData(null, NAMESPACE_TYPE);
  }

  @Test(expectedExceptions = InfrastructureException.class)
  public void shouldThrowInfrastructureExceptionWhenSubjectHasNullToken() throws Exception {
    // Given
    when(subject.getToken()).thenReturn(null);

    // When
    tenantDataProvider.getUserCheTenantData(subject, NAMESPACE_TYPE);
  }
}
