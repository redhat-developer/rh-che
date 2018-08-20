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
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.redhat.che.selenium.core.client.RhCheTestWorkspaceServiceClient;
import com.redhat.che.selenium.core.workspace.RhCheTestWorkspaceProvider;
import org.eclipse.che.selenium.core.client.TestWorkspaceServiceClient;
import org.eclipse.che.selenium.core.client.TestWorkspaceServiceClientFactory;
import org.eclipse.che.selenium.core.configuration.SeleniumTestConfiguration;
import org.eclipse.che.selenium.core.configuration.TestConfiguration;
import org.eclipse.che.selenium.core.provider.DefaultTestUserProvider;
import org.eclipse.che.selenium.core.user.DefaultTestUser;
import org.eclipse.che.selenium.core.user.MultiUserCheDefaultTestUserProvider;
import org.eclipse.che.selenium.core.workspace.TestWorkspaceProvider;

public class RhCheSeleniumSuiteModule extends AbstractModule {

  @Override
  protected void configure() {
    TestConfiguration config = new SeleniumTestConfiguration();
    config.getMap().forEach((key, value) -> bindConstant().annotatedWith(named(key)).to(value));
    bind(DefaultTestUserProvider.class).to(MultiUserCheDefaultTestUserProvider.class);
    bind(DefaultTestUser.class).toProvider(DefaultTestUserProvider.class);
    install(
        new FactoryModuleBuilder()
            .implement(TestWorkspaceServiceClient.class, RhCheTestWorkspaceServiceClient.class)
            .build(TestWorkspaceServiceClientFactory.class));
    bind(TestWorkspaceServiceClient.class).to(RhCheTestWorkspaceServiceClient.class);
    bind(TestWorkspaceProvider.class).to(RhCheTestWorkspaceProvider.class).asEagerSingleton();
  }
}
