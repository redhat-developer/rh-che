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

import static java.lang.String.format;

import com.redhat.che.selenium.core.client.RhCheTestWorkspaceServiceClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.che.api.core.model.workspace.Workspace;
import org.eclipse.che.selenium.core.user.TestUser;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author Katerina Foniok */
public class ProvidedWorkspace implements TestWorkspace {

  private static final Logger LOG = LoggerFactory.getLogger(ProvidedWorkspace.class);

  private CompletableFuture<Void> future;
  private AtomicReference<String> id;
  private String workspaceName;
  private TestUser owner;
  private RhCheTestWorkspaceServiceClient workspaceServiceClient;

  public ProvidedWorkspace(
      TestUser owner,
      RhCheTestWorkspaceServiceClient testWorkspaceServiceClient,
      String givenWorkspaceName) {
    this.id = new AtomicReference<>();
    this.owner = owner;
    this.workspaceServiceClient = testWorkspaceServiceClient;

    this.future =
        CompletableFuture.runAsync(
            () -> {
              try {
                final Workspace ws =
                    workspaceServiceClient.findExistingWorkspace(givenWorkspaceName);
                this.id.set(ws.getId());
                this.workspaceName = ws.getConfig().getName();
                long start = System.currentTimeMillis();
                workspaceServiceClient.start(this.id.get(), this.workspaceName, this.owner);
                LOG.info(
                    "Workspace name='{}' id='{}' started in {} sec.",
                    workspaceName,
                    ws.getId(),
                    (System.currentTimeMillis() - start) / 1000);
              } catch (Exception e) {
                String errorMessage =
                    format("Workspace name='%s' start failed.", this.workspaceName);
                LOG.error(errorMessage, e);
                throw new IllegalStateException(errorMessage, e);
              }
            });
  }

  @Override
  public void await() throws InterruptedException, ExecutionException {
    this.future.get();
  }

  @Override
  public String getName() throws ExecutionException, InterruptedException {
    return this.future.thenApply(aVoid -> this.workspaceName).get();
  }

  @Override
  public String getId() throws ExecutionException, InterruptedException {
    return this.future.thenApply(aVoid -> this.id.get()).get();
  }

  @Override
  public TestUser getOwner() {
    return this.owner;
  }

  @Override
  public void delete() {
    // the provided workspace should not be deleted
  }

  public String getProjectGitUrl(int projectSerialNumber) throws Exception {
    return workspaceServiceClient.getProjectGitUrl(this.workspaceName, projectSerialNumber);
  }
}
