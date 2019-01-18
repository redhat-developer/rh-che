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

import com.google.inject.AbstractModule;
import org.eclipse.che.selenium.pageobject.site.LoginPage;

public class RhCheSeleniumWebDriverRelatedModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(LoginPage.class).to(RhCheLoginPage.class);
  }
}
