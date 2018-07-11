/*
 * Copyright (c) 2016-2018 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
import javax.annotation.PreDestroy;
import org.eclipse.che.api.core.model.workspace.Workspace;
import org.eclipse.che.selenium.core.user.TestUser;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

/** @author Anatolii Bazko */
public class RhCheTestWorkspaceImpl implements TestWorkspace {

  private static final Logger LOG = LoggerFactory.getLogger(RhCheTestWorkspaceImpl.class);

  private CompletableFuture<Void> future;
  private AtomicReference<String> id;
  private AtomicReference<String> workspaceName;
  private AtomicReference<TestUser> owner;
  private RhCheTestWorkspaceServiceClient workspaceServiceClient;

  public RhCheTestWorkspaceImpl(
      TestUser owner, RhCheTestWorkspaceServiceClient testWorkspaceServiceClient) {
    this.id = new AtomicReference<>();
    this.workspaceName = new AtomicReference<>();
    this.owner = new AtomicReference<>();
    this.owner.set(owner);
    this.workspaceServiceClient = testWorkspaceServiceClient;
    if (this.workspaceServiceClient == null) {
      throw new IllegalArgumentException(
          "workspaceServiceClient is null. Probably AbstractTestWorkspaceServiceClient is not instance of RhChe...");
    }

    this.future =
        CompletableFuture.runAsync(
            () -> {
              try {
                final Workspace ws = workspaceServiceClient.createWorkspaceWithCheStarter();
                this.id.set(ws.getId());
                this.workspaceName.set(ws.getConfig().getName());
                long start = System.currentTimeMillis();
                workspaceServiceClient.start(
                    this.id.get(), this.workspaceName.get(), this.owner.get());
                LOG.info(
                    "Workspace name='{}' id='{}' started in {} sec.",
                    workspaceName.get(),
                    ws.getId(),
                    (System.currentTimeMillis() - start) / 1000);
              } catch (Exception e) {
                String errorMessage =
                    format("Workspace name='%s' start failed.", this.workspaceName.get());
                LOG.error(errorMessage, e);

                try {
                  workspaceServiceClient.delete(
                      this.workspaceName.get(), this.owner.get().getName());
                } catch (Exception e1) {
                  LOG.error(
                      "Failed to remove workspace name='{}' when start is failed.",
                      this.workspaceName.get());
                }

                if (e instanceof IllegalStateException) {
                  Assert.fail("Known issue https://github.com/eclipse/che/issues/8856", e);
                } else {
                  throw new IllegalStateException(errorMessage, e);
                }
              }
            });
  }

  @Override
  public void await() throws InterruptedException, ExecutionException {
    this.future.get();
  }

  @Override
  public String getName() throws ExecutionException, InterruptedException {
    return this.future.thenApply(aVoid -> this.workspaceName.get()).get();
  }

  @Override
  public String getId() throws ExecutionException, InterruptedException {
    return this.future.thenApply(aVoid -> this.id.get()).get();
  }

  @Override
  public TestUser getOwner() {
    return this.owner.get();
  }

  @PreDestroy
  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void delete() {
    this.future.thenAccept(
        aVoid -> {
          try {
            this.workspaceServiceClient.delete(
                this.workspaceName.get(), this.owner.get().getName());
          } catch (Exception e) {
            throw new RuntimeException(format("Failed to remove workspace '%s'", this), e);
          }
        });
  }
}
