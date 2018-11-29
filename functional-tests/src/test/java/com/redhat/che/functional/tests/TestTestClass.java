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
import com.redhat.che.selenium.core.workspace.CheStarterWrapper;
import com.redhat.che.selenium.core.workspace.RhCheTestWorkspaceImpl;
import org.eclipse.che.selenium.core.user.DefaultTestUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

public class TestTestClass {

  private static final Logger LOG = LoggerFactory.getLogger(TestTestClass.class);

  @Inject private DefaultTestUser defaultTestUser;

  @Inject private CheStarterWrapper cheStarterWrapper;

  @Inject private RhCheTestWorkspaceImpl testWorkspace;

  @Test
  public void dummyTestCase() throws Exception {
    // Get required resources
    String createWorkspaceRequestJson = "/templates/workspace/che-starter/che-starter_vertx.json";
    String token = defaultTestUser.obtainAuthToken();

    // Verify che-starter running
    boolean cheStarterStatus;
    try {
      cheStarterStatus = cheStarterWrapper.checkIsRunning(token);
    } catch (RuntimeException e) {
      LOG.error("Che starter communication failed:" + e.getMessage(), e);
      throw e;
    }
    if (!cheStarterStatus) {
      String errorMsg = "Che starter liveliness probe failed.";
      LOG.error(errorMsg);
      throw new RuntimeException("Che starter liveliness probe failed.");
    }

    // Create workspace
    String workspaceName;
    try {
      workspaceName = cheStarterWrapper.createWorkspace(createWorkspaceRequestJson, token);
      LOG.info("Workspace {} successfully created.", workspaceName);
    } catch (Exception e) {
      LOG.error("Workspace creation failed:" + e.getMessage(), e);
      throw e;
    }

    // Delete workspace
    try {
      cheStarterWrapper.deleteWorkspace(workspaceName, token);
    } catch (Exception e) {
      LOG.error("Could not delete workspace " + workspaceName + ":" + e.getMessage(), e);
      throw e;
    }

    // Try deleting the workspace again
    try {
      if (!cheStarterWrapper.deleteWorkspace(workspaceName, token)) {
        String errorMsg = "Repetitive deletion of non-existent workspace failed with error.";
        LOG.error(errorMsg);
        throw new RuntimeException(errorMsg);
      } else {
        LOG.info("Repetitive deletion of non-existent workspace - expected behavior. OK.");
      }
    } catch (Exception e) {
      LOG.error(
          "Repetitive deletion of non-existent workspace failed with exception:" + e.getMessage(),
          e);
      throw e;
    }

    LOG.info("Injected test workspace:" + testWorkspace.getName());
  }
}
