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

import com.redhat.che.selenium.core.workspace.RhCheWorkspaceTemplate;
import org.eclipse.che.selenium.core.workspace.InjectTestWorkspace;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;

public class BayesianPackageJson extends BayesianAbstractTestClass {

  @InjectTestWorkspace(template = RhCheWorkspaceTemplate.RH_NODEJS)
  private TestWorkspace workspace;

  private static final Integer JSON_EXPECTED_ERROR_LINE = 12;
  private static final Integer JSON_INJECTION_ENTRY_POINT = 12;
  private static final String JSON_EXPECTED_ERROR_TEXT = "1.7.1";
  private static final String PROJECT_FILE = "package.json";
  private static final String PATH_TO_FILE = "nodejs-hello-world";
  private static final String PROJECT_DEPENDENCY = "\"serve-static\": \"1.7.1\" ,\n";
  private static final String ERROR_MESSAGE =
      "Package serve-static-1.7.1 is vulnerable: CVE-2015-1164. Recommendation: use version";

  @BeforeClass
  public void setUp() throws Exception {
    // Bayesian is not working on prod-preview environment. Remove that SkipException once this
    // issues is fixed.
    // Issue: https://github.com/redhat-developer/rh-che/issues/524
    if (isPreview()) {
      throw new SkipException("Skipping bayesian test on prod-preview.");
    }
    setPathToFile(PATH_TO_FILE);
    setExpectedErrorLine(JSON_EXPECTED_ERROR_LINE);
    setExpectedErrorText(JSON_EXPECTED_ERROR_TEXT);
    setInjectionEntryPoint(JSON_INJECTION_ENTRY_POINT);
    setProjectFile(PROJECT_FILE);
    setPathToFile(PATH_TO_FILE);
    setErrorMessage(ERROR_MESSAGE);
    setProjectDependency(PROJECT_DEPENDENCY);
    setWorksapce(workspace);
    openTestFile();
  }
}
