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
import java.util.concurrent.ExecutionException;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.eclipse.che.selenium.pageobject.CodenvyEditor;
import org.eclipse.che.selenium.pageobject.Ide;
import org.eclipse.che.selenium.pageobject.NotificationsPopupPanel;
import org.eclipse.che.selenium.pageobject.ProjectExplorer;
import org.eclipse.che.selenium.pageobject.ProjectExplorer.ProjectExplorerOptionsMenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RhCheAbstractTestClass {

  private static final Logger LOG = LoggerFactory.getLogger(RhCheAbstractTestClass.class);

  public static final String CHE_PROD_PREVIEW_URL = "che.prod-preview.openshift.io";
  public static final String CHE_PROD_URL = "che.openshift.io";

  @Inject private Ide ide;
  @Inject private NotificationsPopupPanel notificationsPopupPanel;
  @Inject private ProjectExplorer projectExplorer;
  @Inject private CodenvyEditor editor;

  @Inject
  @Named("che.host")
  String cheHost;

  public void checkWorkspace(TestWorkspace workspace) throws Exception {
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
      checkProjectPresent(workspace);
    } catch (ExecutionException | InterruptedException e) {
      LOG.error(
          "Could not obtain workspace name and id - worskape was probably not successfully injected.");
      throw e;
    } catch (Exception e) {
      if (e.getMessage().contains("popup")) {
        LOG.error("Timeout when waiting for all popups to be closed.");
      } else {
        LOG.error("Could not open workspace IDE.");
      }
      throw e;
    }
  }

  public void checkProjectPresent(TestWorkspace workspace) throws Exception {
    if (projectExplorer.getNamesOfAllOpenItems().get(0).equals("There are no projects")) {
      LOG.error("Project was not imported.");
      throw new RuntimeException("Project was not imported to the workspace.");
    }
  }

  public void closeFilesAndProject() {
    editor.closeAllTabs();
    projectExplorer.clickOnProjectExplorerOptionsButton();
    projectExplorer.clickOnOptionsMenuItem(ProjectExplorerOptionsMenuItem.COLLAPSE_ALL);
  }

  public void closeBrowser() {
    ide.close();
  }

  public boolean isProd() {
    return CHE_PROD_URL.equals(cheHost);
  }

  public boolean isPreview() {
    return CHE_PROD_PREVIEW_URL.equals(cheHost);
  }
}
