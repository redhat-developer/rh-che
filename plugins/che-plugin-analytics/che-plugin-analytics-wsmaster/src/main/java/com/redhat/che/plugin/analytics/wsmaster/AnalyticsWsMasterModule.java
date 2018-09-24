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
package com.redhat.che.plugin.analytics.wsmaster;

import com.google.inject.AbstractModule;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matchers;
import java.lang.reflect.Method;
import org.eclipse.che.api.factory.server.FactoryParametersResolver;
import org.eclipse.che.api.factory.shared.dto.FactoryDto;
import org.eclipse.che.api.workspace.server.WorkspaceRuntimes;
import org.eclipse.che.inject.DynaModule;
import org.eclipse.che.multiuser.keycloak.server.AbstractKeycloakFilter;

/**
 * Module that allows pushing workspace events to the Segment Analytics tracking tool
 *
 * @author David festal
 */
@DynaModule
public class AnalyticsWsMasterModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(AnalyticsService.class);
    bind(AttributesCompleter.class);

    final FactoryUrlSetterInterceptor factoryUrlSetterInterceptor =
        new FactoryUrlSetterInterceptor();
    requestInjection(factoryUrlSetterInterceptor);
    bindInterceptor(
        Matchers.subclassesOf(FactoryParametersResolver.class),
        Matchers.returns(Matchers.subclassesOf(FactoryDto.class)),
        factoryUrlSetterInterceptor);

    final StartNumberSetterInterceptor startNumberSetterInterceptor =
        new StartNumberSetterInterceptor();
    requestInjection(startNumberSetterInterceptor);
    bindInterceptor(
        Matchers.subclassesOf(WorkspaceRuntimes.class),
        new AbstractMatcher<Method>() {
          @Override
          public boolean matches(Method m) {
            return "startAsync".equals(m.getName());
          }
        },
        startNumberSetterInterceptor);

    final DisableAuthenticationInterceptor disableAuthenticationInterceptor =
        new DisableAuthenticationInterceptor();
    requestInjection(disableAuthenticationInterceptor);
    bindInterceptor(
        Matchers.subclassesOf(AbstractKeycloakFilter.class),
        new AbstractMatcher<Method>() {
          @Override
          public boolean matches(Method m) {
            return "doFilter".equals(m.getName());
          }
        },
        disableAuthenticationInterceptor);
  }
}
