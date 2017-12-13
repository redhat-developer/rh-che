/*
 * Copyright (c) 2016-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.che.wsmaster.deploy;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import org.eclipse.che.inject.DynaModule;
import org.eclipse.che.multiuser.keycloak.token.provider.oauth.OpenShiftGitHubOAuthAuthenticator;
import org.eclipse.che.security.oauth.OAuthAuthenticator;

/** @author David Festal */
@DynaModule
public class Fabric8WsMasterModule extends AbstractModule {
  @Override
  protected void configure() {
    Multibinder<OAuthAuthenticator> oAuthAuthenticators =
        Multibinder.newSetBinder(binder(), OAuthAuthenticator.class);
    oAuthAuthenticators.addBinding().to(OpenShiftGitHubOAuthAuthenticator.class);
  }
}
