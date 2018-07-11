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
    modules.add(
        Modules.override(new CheSeleniumSuiteModule()).with(new RhCheSeleniumSuiteModule()));
    return modules;
  }

  @Override
  public List<Module> getChildModules() {
    List<Module> modules = new ArrayList<>();
    modules.add(new CheSeleniumWebDriverRelatedModule());
    return modules;
  }
}
