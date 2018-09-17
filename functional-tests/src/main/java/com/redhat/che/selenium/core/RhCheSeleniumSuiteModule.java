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
package com.redhat.che.selenium.core;

import static com.google.inject.name.Names.named;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import com.redhat.che.selenium.core.client.RhCheTestWorkspaceServiceClient;
import com.redhat.che.selenium.core.workspace.ProvidedWorkspace;
import com.redhat.che.selenium.core.workspace.RhCheTestWorkspaceImpl;
import com.redhat.che.selenium.core.workspace.RhCheTestWorkspaceProvider;
import org.eclipse.che.selenium.core.client.TestWorkspaceServiceClient;
import org.eclipse.che.selenium.core.client.TestWorkspaceServiceClientFactory;
import org.eclipse.che.selenium.core.configuration.SeleniumTestConfiguration;
import org.eclipse.che.selenium.core.configuration.TestConfiguration;
import org.eclipse.che.selenium.core.provider.DefaultTestUserProvider;
import org.eclipse.che.selenium.core.user.DefaultTestUser;
import org.eclipse.che.selenium.core.user.MultiUserCheDefaultTestUserProvider;
import org.eclipse.che.selenium.core.user.TestUser;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.eclipse.che.selenium.core.workspace.TestWorkspaceProvider;

public class RhCheSeleniumSuiteModule extends AbstractModule {

  @Override
  protected void configure() {
    TestConfiguration config = new SeleniumTestConfiguration();
    config.getMap().forEach((key, value) -> bindConstant().annotatedWith(named(key)).to(value));
    bind(DefaultTestUserProvider.class).to(MultiUserCheDefaultTestUserProvider.class);
    bind(DefaultTestUser.class).toProvider(DefaultTestUserProvider.class);
    bind(TestUser.class).to(DefaultTestUser.class);
    install(
        new FactoryModuleBuilder()
            .implement(TestWorkspaceServiceClient.class, RhCheTestWorkspaceServiceClient.class)
            .build(TestWorkspaceServiceClientFactory.class));
    bind(TestWorkspaceServiceClient.class).to(RhCheTestWorkspaceServiceClient.class);
    bind(TestWorkspaceProvider.class).to(RhCheTestWorkspaceProvider.class).asEagerSingleton();
    if (config.getMap().get("che.workspaceName") == null) {
      bind(String.class)
          .annotatedWith(Names.named("che.workspaceName"))
          .toProvider(Providers.<String>of(null));
    }
  }

  @Provides
  public TestWorkspace getWorkspace(
      TestWorkspaceProvider workspaceProvider,
      DefaultTestUser testUser,
      @Named("workspace.default_memory_gb") int defaultMemoryGb)
      throws Exception {
    TestWorkspace ws =
        workspaceProvider.createWorkspace(testUser, defaultMemoryGb, "default.json", true);
    ws.await();
    return ws;
  }

  @Provides
  public RhCheTestWorkspaceImpl getRhCheWorkspace(
      TestWorkspaceProvider workspaceProvider,
      DefaultTestUser testUser,
      @Named("workspace.default_memory_gb") int defaultMemoryGb)
      throws Exception {
    RhCheTestWorkspaceImpl ws =
        (RhCheTestWorkspaceImpl)
            workspaceProvider.createWorkspace(testUser, defaultMemoryGb, "default.json", true);
    ws.await();
    return ws;
  }

  @Provides
  public ProvidedWorkspace getProvidedWorkspace(
      RhCheTestWorkspaceProvider workspaceProvider,
      DefaultTestUser testUser,
      @Named("che.workspaceName") String givenWorkspaceName)
      throws Exception {
    if (givenWorkspaceName == null) {
      throw new RuntimeException(
          "Variable `che.workspaceName` must be set to use ProvidedWorkspace");
    }
    ProvidedWorkspace ws = workspaceProvider.findWorkspace(testUser, givenWorkspaceName);
    ws.await();
    return ws;
  }
}
