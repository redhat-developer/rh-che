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
package com.redhat.che.functional.tests;

import com.google.inject.Inject;
import com.redhat.che.selenium.core.workspace.RhCheWorkspaceTemplate;
import org.eclipse.che.selenium.core.SeleniumWebDriver;
import org.eclipse.che.selenium.core.workspace.InjectTestWorkspace;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.eclipse.che.selenium.pageobject.Consoles;
import org.eclipse.che.selenium.pageobject.ProjectExplorer;
import org.eclipse.che.selenium.pageobject.intelligent.CommandsPalette;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class BuildAndRunProjectTest extends RhCheAbstractTestClass {

  private static final Logger LOG = LoggerFactory.getLogger(BuildAndRunProjectTest.class);

  @InjectTestWorkspace(template = RhCheWorkspaceTemplate.RH_VERTX)
  private TestWorkspace workspace;

  @Inject private ProjectExplorer projectExplorer;
  @Inject private CommandsPalette commandsPalette;
  @Inject private Consoles consoles;
  @Inject private SeleniumWebDriver seleniumWebDriver;

  @BeforeClass
  public void checkWorkspace() throws Exception {
    checkWorkspace(workspace);
    int maxTries = 5, counter = 0;

    // This while loop is a workaround for issue:
    // https://github.com/openshiftio/openshift.io/issues/4695
    // If project is not imported, refresh whole page. Try for <maxTires> attempts.
    while (projectExplorer.getNamesOfAllOpenItems().get(0).equals("There are no projects")) {
      counter++;
      LOG.warn(
          "Project was not imported. Trying to refresh the page to import the project. Attempt " + counter + "/5");
      seleniumWebDriver.get(seleniumWebDriver.getCurrentUrl());
      checkWorkspace(workspace);
      if (counter == maxTries) {
        LOG.error("Project was not imported in " + maxTries + " tries.");
        throw new RuntimeException("Project was not imported to the workspace.");
      }
    }
  }

  @Test
  public void runProjectBuild() {
    commandsPalette.openCommandPalette();
    commandsPalette.startCommandByEnterKey("run");
    consoles.waitExpectedTextIntoConsole("INFO: Succeeded in deploying verticle", 180);
  }
}
