/*
 * Copyright (c) 2016-2018 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.che.multitenant;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.multiuser.keycloak.server.deploy.OAuthAPIProvider;
import org.eclipse.che.security.oauth.OAuthAPI;

public class Fabric8OAuthAPIProvider extends OAuthAPIProvider {

  private Injector injector;

  @Inject
  public Fabric8OAuthAPIProvider(
      @Nullable @Named("che.oauth.service_mode") String oauthType, Injector injector) {
    super(oauthType, injector);
    this.injector = injector;
  }

  @Override
  public OAuthAPI get() {
    return injector.getInstance(Fabric8DelegatedOAuthAPI.class);
  }
}
