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
package com.redhat.che.selenium.core.workspace;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.redhat.che.selenium.core.client.RhCheTestWorkspaceServiceClient;
import org.eclipse.che.selenium.core.user.TestUser;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.eclipse.che.selenium.core.workspace.TestWorkspaceProvider;

@Singleton
public class RhCheTestWorkspaceProvider implements TestWorkspaceProvider {

  private final RhCheTestWorkspaceServiceClient rhcheWorksapceClient;

  @Inject
  protected RhCheTestWorkspaceProvider(RhCheTestWorkspaceServiceClient testWorkspaceServiceClient) {
    this.rhcheWorksapceClient = testWorkspaceServiceClient;
  }

  @Override
  public TestWorkspace createWorkspace(
      TestUser owner, int memoryGB, String templateFileName, boolean startAfterCreation) {
    return new RhCheTestWorkspaceImpl(
        owner, rhcheWorksapceClient, templateFileName, startAfterCreation);
  }

  @Override
  public void shutdown() {}

  @Override
  public ProvidedWorkspace getWorkspace(String workspaceName, TestUser owner) {
    return new ProvidedWorkspace(owner, rhcheWorksapceClient, workspaceName);
  }
}
