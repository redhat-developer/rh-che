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
import org.eclipse.che.selenium.core.provider.TestApiEndpointUrlProvider;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.eclipse.che.selenium.pageobject.CodenvyEditor;
import org.eclipse.che.selenium.pageobject.ProjectExplorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public abstract class BayesianAbstractTestClass extends RhCheAbstractTestClass {

  private static final Logger LOG = LoggerFactory.getLogger(BayesianAbstractTestClass.class);

  private TestWorkspace workspace;

  @Inject private CodenvyEditor editor;
  @Inject private ProjectExplorer projectExplorer;
  @Inject private TestApiEndpointUrlProvider testApiEndpointUrlProvider;

  private Integer EXPECTED_ERROR_LINE;
  private Integer INJECTION_ENTRY_POINT;
  private String EXPECTED_ERROR_TEXT;
  private String PROJECT_FILE;
  private String PATH_TO_FILE;
  private String PROJECT_DEPENDENCY;
  private String ERROR_MESSAGE;

  public void setExpectedErrorLine(Integer expectedErrorLine) {
    EXPECTED_ERROR_LINE = expectedErrorLine;
  }

  public void setInjectionEntryPoint(Integer injectionEntryPoint) {
    INJECTION_ENTRY_POINT = injectionEntryPoint;
  }

  public void setExpectedErrorText(String expectedErrorText) {
    EXPECTED_ERROR_TEXT = expectedErrorText;
  }

  public void setProjectFile(String projectFile) {
    PROJECT_FILE = projectFile;
  }

  public void setPathToFile(String pathToFile) {
    PATH_TO_FILE = pathToFile;
  }

  public void setProjectDependency(String projectDependency) {
    PROJECT_DEPENDENCY = projectDependency;
  }

  public void setErrorMessage(String errorMessage) {
    ERROR_MESSAGE = errorMessage;
  }

  public void setWorksapce(TestWorkspace workspace) {
    this.workspace = workspace;
  }

  public void openTestFile() throws Exception {
    LOG.info("Waiting for workspace to be ready.");
    checkWorkspace(workspace);
  }

  @BeforeMethod
  public void prepareProjectFile() {
    projectExplorer.expandPathInProjectExplorerAndOpenFile(PATH_TO_FILE, PROJECT_FILE);
    appendDependency();
  }

  @AfterMethod
  public void closeFiles() {
    removeDependency();
    closeFilesAndProject();
  }

  @AfterClass
  public void closeBrowserAfterTest() {
    super.closeBrowser();
  }

  @Test
  public void checkBayesianError() {
    editorCheckBayesianError();
    LOG.info("Bayesian error message was present after adding dependency.");
  }

  @Test
  public void checkErrorPresentAfterReopenFile() {
    closeFilesAndProject();
    projectExplorer.expandPathInProjectExplorerAndOpenFile(PATH_TO_FILE, PROJECT_FILE);
    editorCheckBayesianError();
    LOG.info("Bayesian error message was present after reopening file.");
  }

  protected void appendDependency() {
    editor.setCursorToLine(INJECTION_ENTRY_POINT);
    editor.typeTextIntoEditor(PROJECT_DEPENDENCY);
    editor.waitTabFileWithSavedStatus(PROJECT_FILE);
  }

  protected void removeDependency() {
    editor.setCursorToLine(INJECTION_ENTRY_POINT);
    editor.deleteCurrentLine();
    editor.waitTabFileWithSavedStatus(PROJECT_FILE);
  }

  protected void editorCheckBayesianError() {
    editor.setCursorToLine(EXPECTED_ERROR_LINE);
    editor.moveCursorToText(EXPECTED_ERROR_TEXT);
    editor.waitTextInToolTipPopup(ERROR_MESSAGE);
  }

  protected TestApiEndpointUrlProvider getTestApiEndpointUrlProvider() {
    return this.testApiEndpointUrlProvider;
  }
}
