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

import com.google.inject.Inject;
import com.redhat.che.selenium.core.client.RhCheTestWorkspaceServiceClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.PreDestroy;
import org.eclipse.che.api.core.model.workspace.Workspace;
import org.eclipse.che.api.core.model.workspace.WorkspaceStatus;
import org.eclipse.che.selenium.core.user.TestUser;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author Tibor Dancs */
public class RhCheTestWorkspaceImpl implements TestWorkspace {

  private static final Logger LOG = LoggerFactory.getLogger(RhCheTestWorkspaceImpl.class);

  private CompletableFuture<Void> future;
  private AtomicReference<String> id;
  private String workspaceName;
  private TestUser owner;
  private RhCheTestWorkspaceServiceClient workspaceServiceClient;
  private boolean startAfterCreation;

  @Inject
  public RhCheTestWorkspaceImpl(
      TestUser owner,
      RhCheTestWorkspaceServiceClient testWorkspaceServiceClient,
      boolean startAfterCreation) {
    this.startAfterCreation = startAfterCreation;
    this.id = new AtomicReference<>();
    this.owner = owner;
    this.workspaceServiceClient = testWorkspaceServiceClient;
    if (this.workspaceServiceClient == null) {
      throw new IllegalArgumentException(
          "workspaceServiceClient is null. Probably AbstractTestWorkspaceServiceClient is not instance of RhChe...");
    }

    this.future =
        CompletableFuture.runAsync(
            () -> {
              try {
                LOG.info("Creating new workspace with che-starter.");
                final Workspace ws = workspaceServiceClient.createWorkspaceWithCheStarter();
                this.id.set(ws.getId());
                this.workspaceName = ws.getConfig().getName();
                long start = System.currentTimeMillis();
                if (startAfterCreation) {
                  workspaceServiceClient.start(this.id.get(), this.workspaceName, this.owner);
                  LOG.info(
                      "Workspace name='{}' id='{}' started in {} sec.",
                      workspaceName,
                      ws.getId(),
                      (System.currentTimeMillis() - start) / 1000);
                } else {
                  LOG.info(
                      "Workspace "
                          + workspaceName
                          + " should not be started directly after creation - skipping.");
                }
              } catch (Exception e) {
                String errorMessage =
                    format("Workspace name='%s' start failed.", this.workspaceName);
                LOG.error(errorMessage, e);
                deleteImmidiatelly();
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

  public void deleteImmidiatelly() {
    try {
      this.workspaceServiceClient.delete(this.workspaceName, this.owner.getName());
    } catch (Exception e) {
      LOG.error("Failed to remove workspace named '%s' '%s'", this.workspaceName);
      throw new RuntimeException(
          format("Failed to remove workspace named '%s' '%s'", this.workspaceName, this), e);
    }
  }

  public void startWorkspace() throws Exception {
    try {
      workspaceServiceClient.start(id.toString(), workspaceName, owner);
    } catch (Exception e) {
      LOG.error("Could not start workspace with name: " + workspaceName);
      throw e;
    }
  }

  @PreDestroy
  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void delete() {
    this.future.thenAccept(
        aVoid -> {
          try {
            this.workspaceServiceClient.delete(this.workspaceName, this.owner.getName());
          } catch (Exception e) {
            LOG.error("Failed to remove workspace named '%s' '%s'", this.workspaceName);
            throw new RuntimeException(
                format("Failed to remove workspace named '%s' '%s'", this.workspaceName, this), e);
          }
        });
  }

  public boolean checkStatus(String status) {
    try {
      WorkspaceStatus actualStatus = workspaceServiceClient.getStatus(id.toString());
      return status.equals(actualStatus.toString());
    } catch (Exception e) {
      LOG.error("Could not get status of workspace named: " + workspaceName);
      e.printStackTrace();
      return false;
    }
  }
}
