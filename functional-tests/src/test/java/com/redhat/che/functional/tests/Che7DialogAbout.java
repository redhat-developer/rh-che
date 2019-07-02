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
import com.google.inject.name.Named;
import java.io.IOException;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.selenium.core.client.TestWorkspaceServiceClient;
import org.eclipse.che.selenium.core.executor.OpenShiftCliCommandExecutor;
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

  private static final String JAVA_MAVEN_DEVFILE_ID = "Java Maven";
  private static final String LEGACY_SELENIUM_STACK_NAME = "che7";
  private static final String WORKSPACE_NAME = NameGenerator.generate("wksp-", 5);
  public static final String CHE_PROD_URL = "che.openshift.io";

  @Inject private Dashboard dashboard;
  @Inject private TheiaIde theiaIde;
  @Inject private DefaultTestUser defaultTestUser;
  @Inject private TestWorkspaceServiceClient workspaceServiceClient;

  @Inject private Workspaces workspaces;
  @Inject private NewWorkspace newWorkspace;
  @Inject private SeleniumWebDriverHelper seleniumWebDriverHelper;
  @Inject private ProjectSourcePage projectSourcePage;
  @Inject private TestWorkspaceProvider testWorkspaceProvider;
  @Inject private OpenShiftCliCommandExecutor cli;

  @Inject
  @Named("che.host")
  String cheHost;

  @AfterClass
  public void tearDown() throws Exception {
    workspaceServiceClient.delete(WORKSPACE_NAME, defaultTestUser.getName());
  }

  @BeforeClass
  public void createAndOpenWorkspace() throws IOException {
    LOG.info("Creating workspace " + WORKSPACE_NAME);
    dashboard.open();
    createChe7Workspace();

    LOG.info("Opening workspace " + WORKSPACE_NAME);
    // following try/catch and logs are there for debugging issue
    // https://github.com/redhat-developer/che-functional-tests/issues/476
    // once that issue is solved, this try/catch can be deleted
    try {
      LOG.info("Switching to IDE frame.");
      theiaIde.switchToIdeFrame();
      LOG.info("Waiting for theia-app-shell for 240 seconds.");
      theiaIde.waitTheiaIde();
      LOG.info("Wait for loader invisibility.");
      theiaIde.waitLoaderInvisibility();
      LOG.info("Waiting for theia-app-shell for 240 seconds.");
      theiaIde.waitTheiaIde();
      LOG.info("Waiting for theia-top-panel for 10 seconds.");
      theiaIde.waitTheiaIdeTopPanel();
    } catch (Exception e) {
      // when exception is caught, we want to gather all info about pods, routes etc.
      String switchProject = "project " + defaultTestUser.getName() + "-che";
      cli.execute(switchProject);
      try {
        // the command "get all" fails, but all info we need is in its message
        // so the hack here is to get the result of "get all" and not throw this
        // exception
        LOG.info(cli.execute("get all"));
      } catch (Exception ex) {
        String[] log = ex.getCause().getMessage().split("(Output: |;)");
        LOG.info("\n" + log[1]);
      }
      // throw the exception from the test
      throw e;
    }
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
    prepareWorkspace(JAVA_MAVEN_DEVFILE_ID, WORKSPACE_NAME, null);
    projectSourcePage.clickOnAddOrImportProjectButton();
    newWorkspace.clickOnCreateButtonAndOpenInIDE();
    return testWorkspaceProvider.getWorkspace(WORKSPACE_NAME, defaultTestUser);
  }

  private void prepareWorkspace(String devfileID, String workspaceName, Double machineRam) {
    dashboard.waitDashboardToolbarTitle();
    LOG.info("Opening Workspaces from left menu...");
    dashboard.selectWorkspacesItemOnDashboard();
    workspaces.clickOnAddWorkspaceBtn();
    newWorkspace.waitToolbar();
    LOG.info("Selecting stack...");
    selectDevfile(devfileID);
    LOG.info("Setting workspace name...");
    newWorkspace.typeWorkspaceName(workspaceName);
  }

  private void selectDevfile(String devfileID) {
    if (isProd()) {
      seleniumWebDriverHelper.waitAndClick(
          By.xpath("//div[@data-stack-id='" + LEGACY_SELENIUM_STACK_NAME + "']"));
    } else {
      seleniumWebDriverHelper.waitAndClick(By.xpath("//div[@data-devfile-id='" + devfileID + "']"));
    }
  }

  private boolean isProd() {
    return CHE_PROD_URL.equals(cheHost);
  }
}
