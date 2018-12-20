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
import org.eclipse.che.selenium.core.workspace.InjectTestWorkspace;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.eclipse.che.selenium.pageobject.Consoles;
import org.eclipse.che.selenium.pageobject.intelligent.CommandsPalette;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class BuildAndRunProjectTest extends RhCheAbstractTestClass {

  @InjectTestWorkspace(template = RhCheWorkspaceTemplate.RH_VERTX)
  private TestWorkspace workspace;

  @Inject private CommandsPalette commandsPalette;
  @Inject private Consoles consoles;

  @BeforeClass
  public void checkWorkspace() throws Exception {
    checkWorkspace(workspace);
  }

  @Test
  public void runProjectBuild() {
    commandsPalette.openCommandPalette();
    commandsPalette.startCommandByEnterKey("run");
    consoles.waitExpectedTextIntoConsole("INFO: Succeeded in deploying verticle", 180);
  }
}
