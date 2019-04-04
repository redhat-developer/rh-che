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
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.selenium.core.client.TestWorkspaceServiceClient;
import org.eclipse.che.selenium.core.user.DefaultTestUser;
import org.eclipse.che.selenium.core.webdriver.SeleniumWebDriverHelper;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.eclipse.che.selenium.core.workspace.TestWorkspaceProvider;
import org.eclipse.che.selenium.pageobject.dashboard.Dashboard;
import org.eclipse.che.selenium.pageobject.dashboard.NewWorkspace;
import org.eclipse.che.selenium.pageobject.dashboard.ProjectSourcePage;
import org.eclipse.che.selenium.pageobject.dashboard.workspaces.Workspaces;
import org.eclipse.che.selenium.pageobject.theia.TheiaIde;
import org.openqa.selenium.By;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class Che7DialogAbout {

  private static final Logger LOG = LoggerFactory.getLogger(Che7DialogAbout.class);

  private static final String WORKSPACE_NAME = NameGenerator.generate("wksp-", 5);

  @Inject private Dashboard dashboard;
  @Inject private TheiaIde theiaIde;
  @Inject private DefaultTestUser defaultTestUser;
  @Inject private TestWorkspaceServiceClient workspaceServiceClient;

  @Inject private Workspaces workspaces;
  @Inject private NewWorkspace newWorkspace;
  @Inject private SeleniumWebDriverHelper seleniumWebDriverHelper;
  @Inject private ProjectSourcePage projectSourcePage;
  @Inject private TestWorkspaceProvider testWorkspaceProvider;

  @AfterClass
  public void tearDown() throws Exception {
    workspaceServiceClient.delete(WORKSPACE_NAME, defaultTestUser.getName());
  }

  @BeforeClass
  public void createAndOpenWorkspace() {
    LOG.info("Creating workspace " + WORKSPACE_NAME);
    dashboard.open();
    createChe7Workspace();

    LOG.info("Opening workspace " + WORKSPACE_NAME);
    theiaIde.switchToIdeFrame();
    theiaIde.waitTheiaIde();
    theiaIde.waitLoaderInvisibility();
    theiaIde.waitTheiaIde();
    theiaIde.waitTheiaIdeTopPanel();
  }

  @Test
  public void openDialogAbout() {
    LOG.info("Opening Dialog About");
    try {
      theiaIde.clickOnMenuItemInMainMenu("Help");
      theiaIde.clickOnSubmenuItem("About");
      theiaIde.waitAboutDialogIsOpen();

    } catch (Exception e) {
      LOG.error(e.getMessage());
      throw e;
    }
  }

  private TestWorkspace createChe7Workspace() {
    prepareWorkspace("che7", WORKSPACE_NAME, null);
    projectSourcePage.clickOnAddOrImportProjectButton();
    newWorkspace.clickOnCreateButtonAndOpenInIDE();
    return testWorkspaceProvider.getWorkspace(WORKSPACE_NAME, defaultTestUser);
  }

  private void prepareWorkspace(String stack, String workspaceName, Double machineRam) {
    dashboard.waitDashboardToolbarTitle();
    dashboard.selectWorkspacesItemOnDashboard();
    workspaces.clickOnAddWorkspaceBtn();
    newWorkspace.waitToolbar();
    newWorkspace.clickOnAllStacksTab();
    selectStack();
    newWorkspace.typeWorkspaceName(workspaceName);

    if (machineRam != null) {
      newWorkspace.setMachineRAM("dev-machine", machineRam);
    }
  }

  private void selectStack() {
    seleniumWebDriverHelper.waitAndClick(By.xpath("//div[@data-stack-id='che7']"));
  }
}
