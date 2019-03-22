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

import com.google.inject.Module;
import com.google.inject.util.Modules;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.che.selenium.core.CheSeleniumSuiteModule;
import org.eclipse.che.selenium.core.CheSeleniumWebDriverRelatedModule;
import org.eclipse.che.selenium.core.inject.SeleniumTestHandler;

public class RhCheSeleniumTestHandler extends SeleniumTestHandler {

  @Override
  public List<Module> getParentModules() {
    List<Module> modules = new ArrayList<>();
    modules.add(new CheSeleniumSuiteModule());
    return modules;
  }

  @Override
  public List<Module> getChildModules() {
    List<Module> modules = new ArrayList<>();
    modules.add(
        Modules.override(new CheSeleniumWebDriverRelatedModule())
            .with(new RhCheSeleniumWebDriverRelatedModule()));
    return modules;
  }
}
