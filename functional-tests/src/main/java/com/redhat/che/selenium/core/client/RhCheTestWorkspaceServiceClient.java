package com.redhat.che.selenium.core.client;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.redhat.che.selenium.core.workspace.CheStarterWrapper;
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
  private static final String CREATE_WORKSPACE_REQUEST_JSON_PATH = "/configs/create-workspace-request.json";

  private TestUser owner = null;
  private String token = null;
  private CheStarterWrapper cheStarterWrapper = null;

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

  public Workspace createWorkspaceWithCheStarter() throws Exception {
    return createWorkspace(null, 0, null, null);
  }

  @Override
  public Workspace createWorkspace(String workspaceName, int memory, MemoryMeasure memoryUnit,
      WorkspaceConfigDto workspace) throws Exception {
    if (owner == null) {
      throw new IllegalStateException("Workspace does not have an owner.");
    }
    String name = this.cheStarterWrapper.createWorkspace(CREATE_WORKSPACE_REQUEST_JSON_PATH, token);
    return requestFactory.fromUrl(getNameBasedUrl(name, owner.getName())).request()
        .asDto(WorkspaceDto.class);
  }

  @Override
  public void start(String workspaceId, String workspaceName, TestUser workspaceOwner)
      throws Exception {
    try {
      this.cheStarterWrapper.startWorkspace(workspaceId, workspaceName, token);
      waitStatus(workspaceName, owner.getName(), WorkspaceStatus.RUNNING);
      LOG.info("Workspace " + workspaceName + "is running.");
    } catch (Exception e) {
      LOG.error("Failed to start workspace \"" + workspaceName + "\": " + e.getMessage(), e);
      throw e;
    }
  }

  @Override
  public void delete(String workspaceName, String userName) throws Exception {
    try {
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

}
