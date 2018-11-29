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
package com.redhat.che.selenium.core.client;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.redhat.che.selenium.core.workspace.CheStarterWrapper;
import java.io.IOException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.core.model.workspace.Workspace;
import org.eclipse.che.api.core.model.workspace.WorkspaceStatus;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.workspace.shared.dto.WorkspaceConfigDto;
import org.eclipse.che.api.workspace.shared.dto.WorkspaceDto;
import org.eclipse.che.selenium.core.client.AbstractTestWorkspaceServiceClient;
import org.eclipse.che.selenium.core.provider.TestApiEndpointUrlProvider;
import org.eclipse.che.selenium.core.requestfactory.TestUserHttpJsonRequestFactoryCreator;
import org.eclipse.che.selenium.core.user.DefaultTestUser;
import org.eclipse.che.selenium.core.user.TestUser;
import org.eclipse.che.selenium.core.workspace.MemoryMeasure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RhCheTestWorkspaceServiceClient extends AbstractTestWorkspaceServiceClient {

  private static final Logger LOG = LoggerFactory.getLogger(RhCheTestWorkspaceServiceClient.class);

  private TestUser owner;
  private String token;
  private CheStarterWrapper cheStarterWrapper;

  @Inject
  public RhCheTestWorkspaceServiceClient(
      TestApiEndpointUrlProvider apiEndpointProvider,
      HttpJsonRequestFactory requestFactory,
      DefaultTestUser defaultTestUser,
      CheStarterWrapper cheStarterWrapper) {
    super(apiEndpointProvider, requestFactory);
    LOG.warn("TestWorkspaceServiceClient instantiated with request factory - using default owner.");
    this.owner = defaultTestUser;
    this.cheStarterWrapper = cheStarterWrapper;
    this.token = this.owner.obtainAuthToken();
  }

  @AssistedInject
  public RhCheTestWorkspaceServiceClient(
      TestApiEndpointUrlProvider apiEndpointProvider,
      TestUserHttpJsonRequestFactoryCreator userHttpJsonRequestFactoryCreator,
      @Assisted TestUser testUser,
      CheStarterWrapper cheStarterWrapper) {
    super(apiEndpointProvider, userHttpJsonRequestFactoryCreator, testUser);
    LOG.info(
        "TestWorkspaceServiceClient instantiated with RequestFactoryCreator - owner set to provided TestUser.");
    this.owner = testUser;
    this.cheStarterWrapper = cheStarterWrapper;
    this.token = this.owner.obtainAuthToken();
  }

  public Workspace createWorkspaceWithCheStarter(String templateFileName) throws Exception {
    if (owner == null) {
      throw new IllegalStateException("Workspace does not have an owner.");
    }
    this.cheStarterWrapper.checkIsRunning(this.token);
    return requestFactory
        .fromUrl(
            getNameBasedUrl(
                this.cheStarterWrapper.createWorkspace(
                    "/templates/workspace/che-starter/" + templateFileName, token),
                owner.getName()))
        .request()
        .asDto(WorkspaceDto.class);
  }

  @Override
  public Workspace createWorkspace(
      String workspaceName, int memory, MemoryMeasure memoryUnit, WorkspaceConfigDto workspace) {
    throw new UnsupportedOperationException(
        "This method is never supposed to be called. "
            + "Workspace name:"
            + workspaceName
            + " for user:"
            + owner.getEmail());
  }

  @Override
  public void start(String workspaceId, String workspaceName, TestUser workspaceOwner)
      throws Exception {
    if (getStatus(workspaceId).equals(WorkspaceStatus.RUNNING)) {
      LOG.info("Workspace is running - no need to start it.");
      return;
    }
    try {
      this.cheStarterWrapper.checkIsRunning(this.token);
      this.cheStarterWrapper.startWorkspace(workspaceId, workspaceName, token);
      waitStatus(workspaceName, owner.getName(), WorkspaceStatus.RUNNING, 300);
      LOG.info("Workspace " + workspaceName + "is running.");
    } catch (Exception e) {
      LOG.error("Failed to start workspace \"" + workspaceName + "\".");
      throw e;
    }
  }

  @Override
  public void delete(String workspaceName, String userName) throws Exception {
    try {
      this.cheStarterWrapper.checkIsRunning(this.token);
      boolean isDeleted = this.cheStarterWrapper.deleteWorkspace(workspaceName, token);
      if (!isDeleted) {
        LOG.error(
            "Failed to delete workspace \"" + workspaceName + "\": Che-starter response is false");
        throw new IllegalStateException(
            "Failed to delete workspace \"" + workspaceName + "\": Che-starter response is false");
      }
    } catch (Exception e) {
      LOG.error("Failed to delete workspace \"" + workspaceName + "\": " + e.getMessage(), e);
      throw e;
    }
  }

  public Workspace findExistingWorkspace(String workspaceName)
      throws ServerException, UnauthorizedException, ForbiddenException, NotFoundException,
          ConflictException, BadRequestException, IOException {
    if (owner == null) {
      throw new IllegalStateException("Workspace does not have an owner.");
    }
    return requestFactory
        .fromUrl(getNameBasedUrl(workspaceName, owner.getName()))
        .request()
        .asDto(WorkspaceDto.class);
  }

  public void startWithoutPatch(String id) throws Exception {
    this.cheStarterWrapper.checkIsRunning(this.token);
    cheStarterWrapper.sendStartRequest(id, token);
  }

  public String getProjectGitUrl(String workspaceName, int projectSerialNumber)
      throws ServerException, UnauthorizedException, ForbiddenException, NotFoundException,
          ConflictException, BadRequestException, IOException {
    Workspace w = findExistingWorkspace(workspaceName);
    return w.getConfig().getProjects().get(projectSerialNumber).getSource().getLocation();
  }
}
