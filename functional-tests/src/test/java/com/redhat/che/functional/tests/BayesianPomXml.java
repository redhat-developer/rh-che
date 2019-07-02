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
import org.eclipse.che.selenium.pageobject.CodenvyEditor;
import org.openqa.selenium.TimeoutException;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;

public class BayesianPomXml extends BayesianAbstractTestClass {

  @InjectTestWorkspace(template = RhCheWorkspaceTemplate.RH_VERTX)
  private TestWorkspace workspace;

  @Inject private CodenvyEditor editor;

  private static final Integer POM_EXPECTED_ERROR_LINE = 40;
  private static final Integer POM_INJECTION_ENTRY_POINT = 37;
  private static final String POM_EXPECTED_ERROR_TEXT = "1.1.10";
  private static final String PROJECT_FILE = "pom.xml";
  private static final String PATH_TO_FILE = "vertx-http-booster";
  private static final String ERROR_MESSAGE =
      "Package ch.qos.logback:logback-core-1.1.10 is vulnerable: CVE-2017-5929";
  private static final String PROJECT_DEPENDENCY =
      "<dependency>\n"
          + "<groupId>ch.qos.logback</groupId>\n"
          + "<artifactId>logback-core</artifactId>\n"
          + "<version>1.1.10</version>\n"
          + "</dependency>\n";

  @BeforeClass
  public void setUp() throws Exception {
    // Bayesian is not working on prod-preview environment. Remove that SkipException once this
    // issues is fixed.
    // Issue: https://github.com/redhat-developer/rh-che/issues/524
    if (isPreview()) {
      throw new SkipException("Skipping bayesian test on prod-preview.");
    }
    setPathToFile(PATH_TO_FILE);
    setExpectedErrorLine(POM_EXPECTED_ERROR_LINE);
    setExpectedErrorText(POM_EXPECTED_ERROR_TEXT);
    setInjectionEntryPoint(POM_INJECTION_ENTRY_POINT);
    setProjectFile(PROJECT_FILE);
    setPathToFile(PATH_TO_FILE);
    setErrorMessage(ERROR_MESSAGE);
    setProjectDependency(PROJECT_DEPENDENCY);
    setWorksapce(workspace);
    openTestFile();
  }

  @Override
  protected void removeDependency() {
    editor.setCursorToLine(POM_INJECTION_ENTRY_POINT);
    editor.deleteCurrentLine();
    editor.deleteCurrentLine();
    editor.deleteCurrentLine();
    editor.deleteCurrentLine();
    editor.deleteCurrentLine();
    editor.waitTabFileWithSavedStatus(PROJECT_FILE);
  }

  // This method skips test for bayesian on reopening file. When issue is fixed, remove this method.
  // Issue: https://github.com/redhat-developer/rh-che/issues/1256
  @Override
  protected void editorCheckBayesianError() {
    editor.setCursorToLine(POM_EXPECTED_ERROR_LINE);
    editor.moveCursorToText(POM_EXPECTED_ERROR_TEXT);
    try {
      editor.waitTextInToolTipPopup(ERROR_MESSAGE);
    } catch (TimeoutException e) {
      if (getTestApiEndpointUrlProvider().get().getHost().equals(CHE_PROD_URL)) {
        throw new SkipException(
            "Bayesian error not present - known issue: https://github.com/redhat-developer/rh-che/issues/1256.");
      }
      throw e;
    }
  }
}
