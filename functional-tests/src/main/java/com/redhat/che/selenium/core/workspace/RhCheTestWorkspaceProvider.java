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
import org.eclipse.che.selenium.core.workspace.WorkspaceTemplate;

@Singleton
public class RhCheTestWorkspaceProvider implements TestWorkspaceProvider {

  private final RhCheTestWorkspaceServiceClient rhcheWorksapceClient;

  @Inject
  protected RhCheTestWorkspaceProvider(RhCheTestWorkspaceServiceClient testWorkspaceServiceClient) {
    this.rhcheWorksapceClient = testWorkspaceServiceClient;
  }

  @Override
  public TestWorkspace createWorkspace(
      TestUser owner, int memoryGB, WorkspaceTemplate template, boolean startAfterCreation) {
    return new RhCheTestWorkspaceImpl(owner, rhcheWorksapceClient, startAfterCreation);
  }

  public ProvidedWorkspace findWorkspace(TestUser owner, String name) {
    return new ProvidedWorkspace(owner, rhcheWorksapceClient, name);
  }

  @Override
  public void shutdown() {}
}
