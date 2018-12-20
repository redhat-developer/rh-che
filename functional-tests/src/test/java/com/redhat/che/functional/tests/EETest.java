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
import com.redhat.che.selenium.core.workspace.ProvidedWorkspace;
import org.eclipse.che.selenium.core.constant.TestGitConstants;
import org.eclipse.che.selenium.core.constant.TestMenuCommandsConstants;
import org.eclipse.che.selenium.pageobject.CodenvyEditor;
import org.eclipse.che.selenium.pageobject.InformationDialog;
import org.eclipse.che.selenium.pageobject.Loader;
import org.eclipse.che.selenium.pageobject.Menu;
import org.eclipse.che.selenium.pageobject.NavigateToFile;
import org.eclipse.che.selenium.pageobject.git.Git;
import org.eclipse.che.selenium.pageobject.git.GitCompare;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

// class for trying ProvidedWorkspace functionality
public class EETest extends RhCheAbstractTestClass {

  @Inject private ProvidedWorkspace workspace;
  @Inject private NavigateToFile navigateToFile;
  @Inject private Loader loader;
  @Inject private CodenvyEditor editor;
  @Inject private Git git;
  @Inject private Menu menu;
  @Inject private InformationDialog dialog;
  @Inject private GitCompare gitCompare;
  private String text = "protected static final String template = \"Bonjour, %s!\";";
  private String fileName = "HttpApplication", extension = ".java";

  private static final String GIT = "gwt-debug-MenuItem/git-true";
  private static final String COMPARE_TOP = "gwt-debug-topmenu/Git/gitCompareGroup";
  private static final String COMPARE_WITH_BRANCH =
      "gwt-debug-topmenu/Git/Compare/gitCompareWithBranch";

  @BeforeClass
  public void checkWorkspace() throws Exception {
    checkWorkspace(workspace);
  }

  @Test(priority = 1)
  public void openClass() throws Exception {
    navigateToFile.launchNavigateToFileByKeyboard();
    navigateToFile.waitFormToOpen();
    loader.waitOnClosed();
    navigateToFile.typeSymbolInFileNameField(fileName);
    navigateToFile.selectFileByName(fileName + extension);
    loader.waitOnClosed();
    editor.waitActive();
  }

  @Test(priority = 2)
  public void changeLineText() {
    editor.selectLineAndDelete(14);
    editor.typeTextIntoEditor(text);
    editor.waitTabFileWithSavedStatus(fileName);
    editor.closeAllTabs();
  }

  @Test(priority = 3)
  public void gitCommit() {
    // Add file to index
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.ADD_TO_INDEX);
    git.waitGitStatusBarWithMess(TestGitConstants.GIT_ADD_TO_INDEX_SUCCESS);

    // Check status
    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.STATUS);
    loader.waitOnClosed();
    String STATUS_MESSAGE_ONE_FILE =
        " On branch master\n"
            + " Changes to be committed:\n"
            + "  modified:   src/main/java/io/openshift/booster/HttpApplication.java";
    git.waitGitStatusBarWithMess(STATUS_MESSAGE_ONE_FILE);

    menu.runCommand(TestMenuCommandsConstants.Git.GIT, TestMenuCommandsConstants.Git.COMMIT);
    git.waitAndRunCommit("commits from test");
    git.waitGitStatusBarWithMess(TestGitConstants.COMMIT_MESSAGE_SUCCESS);
  }

  @Test(priority = 4)
  public void gitPush() throws Exception {
    git.pushChanges(false);
    git.waitPushFormToClose();
    git.waitGitStatusBarWithMess("Successfully pushed");

    menu.runCommand(GIT, COMPARE_TOP, COMPARE_WITH_BRANCH);
    git.waitGitCompareBranchFormIsOpen();
    git.selectBranchIntoGitCompareBranchForm("origin/master");
    gitCompare.clickOnBranchCompareButton();
    dialog.containsText("There are no changes in the selected item.");
  }
}
