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
import java.util.concurrent.ExecutionException;
import org.eclipse.che.selenium.core.workspace.InjectTestWorkspace;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.eclipse.che.selenium.pageobject.Consoles;
import org.eclipse.che.selenium.pageobject.Ide;
import org.eclipse.che.selenium.pageobject.NotificationsPopupPanel;
import org.eclipse.che.selenium.pageobject.ProjectExplorer;
import org.eclipse.che.selenium.pageobject.intelligent.CommandsPalette;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class BuildAndRunProjectTest {

  private static final Logger LOG = LoggerFactory.getLogger(BuildAndRunProjectTest.class);

  @InjectTestWorkspace(template = RhCheWorkspaceTemplate.RH_VERTX)
  private TestWorkspace workspace;

  @Inject private Ide ide;
  @Inject private CommandsPalette commandsPalette;
  @Inject private Consoles consoles;
  @Inject private ProjectExplorer projectExplorer;
  @Inject private NotificationsPopupPanel notificationsPopupPanel;

  @BeforeClass
  public void checkWorkspace() throws Exception {
    try {
      LOG.info(
          "Workspace with name: "
              + workspace.getName()
              + " and id: "
              + workspace.getId()
              + " was successfully injected. ");
      ide.open(workspace);
      ide.waitOpenedWorkspaceIsReadyToUse();
      projectExplorer.waitProjectExplorer();
      notificationsPopupPanel.waitProgressPopupPanelClose();
    } catch (ExecutionException | InterruptedException e) {
      LOG.error(
          "Could not obtain workspace name and id - worskape was probably not successfully injected.");
      throw e;
    } catch (Exception e) {
      LOG.error("Could not open workspace IDE.");
      throw e;
    }
  }

  @Test
  public void runProjectBuild() {
    commandsPalette.openCommandPalette();
    commandsPalette.startCommandByEnterKey("run");
    consoles.waitExpectedTextIntoConsole("INFO: Succeeded in deploying verticle", 180);
  }
}
