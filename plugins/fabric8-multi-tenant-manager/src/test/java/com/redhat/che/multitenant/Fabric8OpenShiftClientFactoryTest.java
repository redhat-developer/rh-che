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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.google.inject.Provider;
import com.redhat.che.multitenant.toggle.CheServiceAccountTokenToggle;
import io.fabric8.kubernetes.client.Config;
import java.util.Optional;
import okhttp3.EventListener;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.workspace.server.WorkspaceRuntimes;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.RuntimeContext;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.workspace.infrastructure.kubernetes.cache.KubernetesRuntimeStateCache;
import org.eclipse.che.workspace.infrastructure.kubernetes.model.KubernetesRuntimeState;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class Fabric8OpenShiftClientFactoryTest {
  private static final String WS_ID = "testWsID";
  private static final String CURRENT_USER_ID = "currentUserID";
  private static final String OWNER_USER_ID = "ownerUserID";
  private static final String GUESSED_NAMESPACE = "guessedNamespace";

  @Mock private Fabric8WorkspaceEnvironmentProvider environmentProvider;
  @Mock private Config defaultConfig;
  @Mock private Config expectedConfig;
  @Mock private Provider<WorkspaceRuntimes> workspaceRuntimeProvider;
  @Mock private WorkspaceRuntimes workspaceRuntimes;
  @Mock private WorkspaceSubjectsRegistry subjectsRegistry;
  @Mock private KubernetesRuntimeStateCache runtimeStateCache;
  @Mock private CheServiceAccountTokenToggle cheServiceAccountTokenToggle;
  @Mock private KubernetesRuntimeState runtimeState;
  @Mock private EventListener eventListener;
  @Mock private Subject currentSubject;
  @Mock private Subject ownerSubject;

  @SuppressWarnings("rawtypes")
  @Mock
  private RuntimeContext runtimeContext;

  @Mock private RuntimeIdentity runtimeIdentity;

  private Fabric8OpenShiftClientFactory factory;

  @BeforeMethod
  public void setUp() throws Exception {
    lenient()
        .doThrow(new InfrastructureException("null subject"))
        .when(environmentProvider)
        .getWorkspacesOpenshiftConfig(null);
    doReturn(expectedConfig)
        .when(environmentProvider)
        .getWorkspacesOpenshiftConfig(any(Subject.class));
    when(workspaceRuntimeProvider.get()).thenReturn(workspaceRuntimes);
    when(workspaceRuntimes.getRuntimeContext(WS_ID)).thenReturn(Optional.of(runtimeContext));
    when(currentSubject.getUserId()).thenReturn(CURRENT_USER_ID);
    when(runtimeContext.getIdentity()).thenReturn(runtimeIdentity);
    when(runtimeIdentity.getOwnerId()).thenReturn(OWNER_USER_ID);
    lenient().when(subjectsRegistry.getSubject(OWNER_USER_ID)).thenReturn(ownerSubject);

    factory =
        new Fabric8OpenShiftClientFactory(
            environmentProvider,
            workspaceRuntimeProvider,
            subjectsRegistry,
            runtimeStateCache,
            cheServiceAccountTokenToggle,
            true,
            1,
            1,
            1,
            1,
            eventListener);

    EnvironmentContext.getCurrent().setSubject(currentSubject);
  }

  @AfterMethod
  public void tearDown() throws Exception {
    EnvironmentContext.reset();
    verifyZeroInteractions(expectedConfig, defaultConfig);
  }

  @Test
  public void returnsConfigUsingCurrentSubjectWhenWsIdIsNull() throws Exception {
    Config config = factory.buildConfig(defaultConfig, null);

    assertEquals(config, expectedConfig);
    verify(environmentProvider).getWorkspacesOpenshiftConfig(eq(currentSubject));
  }

  @Test
  public void returnsConfigUsingCurrentSubjectWhenThereIsNoRuntimeContextForWsId()
      throws Exception {
    when(workspaceRuntimes.getRuntimeContext(WS_ID)).thenReturn(Optional.empty());

    Config config = factory.buildConfig(defaultConfig, WS_ID);

    assertEquals(config, expectedConfig);
    verify(environmentProvider).getWorkspacesOpenshiftConfig(eq(currentSubject));
  }

  @Test
  public void returnsConfigUsingCurrentSubjectWhenItsIdIsEqualToWsOwnerId() throws Exception {
    when(runtimeIdentity.getOwnerId()).thenReturn(CURRENT_USER_ID);

    Config config = factory.buildConfig(defaultConfig, WS_ID);

    assertEquals(config, expectedConfig);
    verify(environmentProvider).getWorkspacesOpenshiftConfig(eq(currentSubject));
  }

  @Test
  public void
      returnsConfigUsingCurrentSubjectWhenSubjectsRegistryDoesNotContainTheCorrespondingOneAndNoRuntimeState()
          throws Exception {
    when(subjectsRegistry.getSubject(OWNER_USER_ID)).thenThrow(new NotFoundException("test error"));

    Config config = factory.buildConfig(defaultConfig, WS_ID);

    assertEquals(config, expectedConfig);
    verify(environmentProvider).getWorkspacesOpenshiftConfig(eq(currentSubject));
  }

  @Test
  public void
      returnsConfigUsingCurrentSubjectWhenSubjectsRegistryDoesNotContainTheCorrespondingOneAndNotUsingCheSaAndRuntimeStateExists()
          throws Exception {
    when(subjectsRegistry.getSubject(OWNER_USER_ID)).thenThrow(new NotFoundException("test error"));
    when(runtimeStateCache.get(any())).thenReturn(Optional.of(runtimeState));
    when(runtimeState.getNamespace()).thenReturn(GUESSED_NAMESPACE);
    when(cheServiceAccountTokenToggle.useCheServiceAccountToken(OWNER_USER_ID)).thenReturn(false);

    Config config = factory.buildConfig(defaultConfig, WS_ID);

    assertEquals(config, expectedConfig);
    verify(environmentProvider).getWorkspacesOpenshiftConfig(eq(currentSubject));
  }

  @Test
  public void
      returnsConfigUsingGuessedSubjectWhenSubjectsRegistryDoesNotContainTheCorrespondingOneAndUsingCheSaAndRuntimeStateExists()
          throws Exception {
    when(subjectsRegistry.getSubject(OWNER_USER_ID)).thenThrow(new NotFoundException("test error"));
    when(runtimeStateCache.get(any())).thenReturn(Optional.of(runtimeState));
    when(runtimeState.getNamespace()).thenReturn(GUESSED_NAMESPACE);
    when(cheServiceAccountTokenToggle.useCheServiceAccountToken(OWNER_USER_ID)).thenReturn(true);

    Config config = factory.buildConfig(defaultConfig, WS_ID);

    assertEquals(config, expectedConfig);
    verify(environmentProvider)
        .getWorkspacesOpenshiftConfig(eq(new GuessedSubject(OWNER_USER_ID, GUESSED_NAMESPACE)));
  }

  @Test
  public void returnsConfigUsingOwnerSubject() throws Exception {
    Config config = factory.buildConfig(defaultConfig, WS_ID);

    assertEquals(config, expectedConfig);
    verify(environmentProvider).getWorkspacesOpenshiftConfig(eq(ownerSubject));
  }
}
