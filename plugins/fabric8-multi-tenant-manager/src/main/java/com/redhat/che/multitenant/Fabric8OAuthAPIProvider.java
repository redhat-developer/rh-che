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

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.multiuser.keycloak.server.deploy.OAuthAPIProvider;
import org.eclipse.che.security.oauth.OAuthAPI;

/** @author Angel Misevski (amisevsk@redhat.com) */
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
