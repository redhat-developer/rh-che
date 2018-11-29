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
import com.redhat.che.selenium.core.client.RhCheTestWorkspaceServiceClient;
import com.redhat.che.selenium.core.workspace.RhCheTestWorkspaceImpl;
import com.redhat.che.selenium.core.workspace.RhCheWorkspaceTemplate;
import java.util.concurrent.ExecutionException;
import org.eclipse.che.selenium.core.workspace.InjectTestWorkspace;
import org.eclipse.che.selenium.pageobject.dashboard.workspaces.Workspaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

public class WorkspaceTest {

  private static final Logger LOG = LoggerFactory.getLogger(TestTestClass.class);
  // When creating workspace with che-starter, all others are stopped. Therefore it is better to
  // start them in test - when both are created.

  @InjectTestWorkspace(
      startAfterCreation = false,
      template = RhCheWorkspaceTemplate.RH_STARTER_VERTX)
  private RhCheTestWorkspaceImpl workspaceToPass;

  @InjectTestWorkspace(
      startAfterCreation = false,
      template = RhCheWorkspaceTemplate.RH_STARTER_NODEJS)
  private RhCheTestWorkspaceImpl firstWorksapce;

  @Inject private RhCheTestWorkspaceServiceClient workspaceServiceClient;

  @Test
  public void testCorrectWayOfStarting() throws Exception {
    String running = Workspaces.Status.RUNNING;
    String stopped = Workspaces.Status.STOPPED;

    firstWorksapce.startWorkspace();

    firstWorksapce.checkStatus(running);
    workspaceToPass.checkStatus(stopped);

    LOG.info("Workspace statuses checked - OK. Starting second workspace.");
    workspaceToPass.startWorkspace();

    firstWorksapce.checkStatus(stopped);
    workspaceToPass.checkStatus(running);
    LOG.info("Second workspace started successfully.");
  }

  @Test
  public void testWrongWayOfStarting() throws ExecutionException, InterruptedException {
    LOG.info("Try to start " + firstWorksapce.getName() + " without PATCH.");
    try {
      workspaceServiceClient.startWithoutPatch(firstWorksapce.getId());
    } catch (Exception e) {
      LOG.info("Workspace should not be started - test succeded.");
    }
  }
}
