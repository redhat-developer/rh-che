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
package com.redhat.che.wsmaster.deploy;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.redhat.che.multitenant.Fabric8AuthServiceClient;
import com.redhat.che.multitenant.Fabric8OAuthAPIProvider;
import java.lang.reflect.Method;
import org.eclipse.che.inject.DynaModule;
import org.eclipse.che.multiuser.keycloak.server.AbstractKeycloakFilter;
import org.eclipse.che.multiuser.keycloak.server.KeycloakServiceClient;
import org.eclipse.che.multiuser.keycloak.server.deploy.OAuthAPIProvider;
import org.eclipse.che.multiuser.keycloak.token.provider.oauth.OpenShiftGitHubOAuthAuthenticator;
import org.eclipse.che.multiuser.machine.authentication.server.MachineAuthenticatedResource;
import org.eclipse.che.security.oauth.GitHubOAuthAuthenticator;

/** @author David Festal */
@DynaModule
public class Fabric8WsMasterModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(GitHubOAuthAuthenticator.class)
        .to(OpenShiftGitHubOAuthAuthenticator.class)
        .asEagerSingleton();
    bind(OAuthAPIProvider.class).to(Fabric8OAuthAPIProvider.class);
    bind(KeycloakServiceClient.class).to(Fabric8AuthServiceClient.class).asEagerSingleton();
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

    final Multibinder<MachineAuthenticatedResource> machineAuthenticatedResources =
        Multibinder.newSetBinder(binder(), MachineAuthenticatedResource.class);
    machineAuthenticatedResources
        .addBinding()
        .toInstance(
            new MachineAuthenticatedResource(
                "/fabric8-che-analytics", "segmentWriteKey", "woopraDomain", "warning", "error"));

    machineAuthenticatedResources
        .addBinding()
        .toInstance(new MachineAuthenticatedResource("/bayesian", "getBayesianToken"));

    MapBinder<String, String> pluginBrokers =
        MapBinder.newMapBinder(
            binder(),
            String.class,
            String.class,
            Names.named("che.workspace.plugin_broker.images"));
    pluginBrokers
        .addBinding("Theia plugin")
        .to(Key.get(String.class, Names.named("che.workspace.plugin_broker.theia.image")));
  }
}
