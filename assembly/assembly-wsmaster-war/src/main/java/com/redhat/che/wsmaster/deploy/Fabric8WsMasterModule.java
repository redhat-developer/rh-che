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
import org.eclipse.che.inject.DynaModule;
import org.eclipse.che.multiuser.keycloak.token.provider.oauth.OpenShiftGitHubOAuthAuthenticator;
import org.eclipse.che.security.oauth.GitHubOAuthAuthenticator;

/** @author David Festal */
@DynaModule
public class Fabric8WsMasterModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(GitHubOAuthAuthenticator.class)
        .to(OpenShiftGitHubOAuthAuthenticator.class)
        .asEagerSingleton();
  }
}
