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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.redhat.che.selenium.core.model.request.CheServerStartWorkspaceRequest;
import com.redhat.che.selenium.core.model.request.CheStarterCreateWorkspaceRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import javax.ws.rs.HttpMethod;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.core.rest.HttpJsonResponse;
import org.eclipse.che.selenium.core.provider.TestApiEndpointUrlProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author Katerina Kanova (kkanova) */
public class CheStarterWrapper {

  private static final Logger LOG = LoggerFactory.getLogger(CheStarterWrapper.class);
  private static final Gson GSON = new Gson();
  private static final JsonParser PARSER = new JsonParser();

  @Inject(optional = true)
  @Named("sys.cheStarterUrl")
  private String cheStarterURL = "http://localhost:10000";

  private String cheHost;
  private String cheProtocol;
  private String chePort;
  private HttpJsonRequestFactory jsonRequestFactory;
  private TestApiEndpointUrlProvider testApiEndpointUrlProvider;

  @Inject
  public CheStarterWrapper(
      @Named("che.host") String cheHost,
      @Named("che.protocol") String cheProtocol,
      @Named("che.port") String chePort,
      HttpJsonRequestFactory jsonRequestFactory,
      TestApiEndpointUrlProvider testApiEndpointUrlProvider) {
    this.cheHost = cheHost;
    this.cheProtocol = cheProtocol;
    this.chePort = chePort;
    this.jsonRequestFactory = jsonRequestFactory;
    this.testApiEndpointUrlProvider = testApiEndpointUrlProvider;
  }

  /** Checks whether che-starter is already running. Throws RuntimeException otherwise. */
  public boolean checkIsRunning(String token) throws RuntimeException {
    String relativePath = "/workspace";
    String authorization = "Bearer " + token;
    HttpJsonResponse livelinessResponse;
    try {
      livelinessResponse =
          this.jsonRequestFactory
              .fromUrl(this.cheStarterURL.concat(relativePath))
              .setAuthorizationHeader(authorization)
              .addQueryParams(getDefaultQueryParameters())
              .setMethod(HttpMethod.GET)
              .request();
    } catch (Exception e) {
      String errMsg = "Liveliness probe for che-starter failed with exception:" + e.getMessage();
      LOG.error(errMsg, e);
      throw new RuntimeException(errMsg, e);
    }
    if (livelinessResponse == null) {
      String errMsg = "Liveliness probe for che-starter failed. Response is empty";
      LOG.error(errMsg);
      throw new RuntimeException(errMsg);
    }
    if (livelinessResponse.getResponseCode() != 200) {
      String errMsg =
          "Liveliness probe for che-starter failed with HTTP code: "
              + livelinessResponse.getResponseCode();
      LOG.error(errMsg);
      throw new RuntimeException(errMsg);
    }
    return true;
  }

  public String createWorkspace(String pathToJson, String token) throws Exception {
    BufferedReader jsonRequestReader;
    try {
      jsonRequestReader =
          new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(pathToJson)));
    } catch (Exception e) {
      LOG.error("File with json was not found on address: " + pathToJson, e);
      throw e;
    }
    CheStarterCreateWorkspaceRequest requestBody =
        GSON.fromJson(jsonRequestReader, CheStarterCreateWorkspaceRequest.class);
    String relativePath = "/workspace";
    String authorization = "Bearer " + token;
    try {
      return getNameFromResponse(
          this.jsonRequestFactory
              .fromUrl(this.cheStarterURL.concat(relativePath))
              .setAuthorizationHeader(authorization)
              .addQueryParams(getDefaultQueryParameters())
              .setMethod(HttpMethod.POST)
              .setBody(requestBody)
              .request());
    } catch (Exception e) {
      LOG.error("Get name from response failed with exception:" + e.getMessage(), e);
      throw new RuntimeException("Failed to parse createWorkspace response.", e);
    }
  }

  public boolean deleteWorkspace(String workspaceName, String token) throws Exception {
    String relativePath = "/workspace/" + workspaceName;
    String authorization = "Bearer " + token;
    HttpJsonResponse response;
    try {
      response =
          this.jsonRequestFactory
              .fromUrl(this.cheStarterURL.concat(relativePath))
              .setAuthorizationHeader(authorization)
              .addQueryParams(getDefaultQueryParameters())
              .setMethod(HttpMethod.DELETE)
              .request();
    } catch (NotFoundException e) {
      LOG.warn("Workspace could not be deleted because workspace was not found.");
      return true;
    }
    if (response.getResponseCode() == 200) {
      LOG.info("Workspace {} deleted successfully.", workspaceName);
      return true;
    } else {
      LOG.error("Workspace could not be deleted: " + response.asString());
      return false;
    }
  }

  public void startWorkspace(String WorkspaceID, String name, String token) throws Exception {
    patchPrepareEnv(name, token);
    sendStartRequest(WorkspaceID, token);
  }

  public void sendStartRequest(String id, String token) throws Exception {
    String relativePath = "workspace/" + id + "/runtime";
    String authorization = "Bearer " + token;
    CheServerStartWorkspaceRequest body = new CheServerStartWorkspaceRequest();
    HttpJsonResponse response;
    try {
      response =
          this.jsonRequestFactory
              .fromUrl(testApiEndpointUrlProvider.get().toString().concat(relativePath))
              .setAuthorizationHeader(authorization)
              .setBody(body)
              .setMethod(HttpMethod.POST)
              .request();
    } catch (ApiException e) {
      LOG.error("Workspace failed to start because of a server error:" + e.getMessage(), e);
      throw e;
    }
    if (response.getResponseCode() == 200) {
      LOG.info("Workspace was started. Waiting until workspace is running.");
    } else {
      throw new RuntimeException(
          "Workspace failed to start [" + response.getResponseCode() + "]:" + response.asString());
    }
  }

  private void patchPrepareEnv(String workspaceName, String token) throws IOException {
    String relativePath = "/workspace/" + workspaceName;
    String authorization = "Bearer " + token;
    RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=urf-8"), "{}");
    OkHttpClient client = new OkHttpClient.Builder().build();
    Response response;

    StringBuilder cheServerURLBuilder = new StringBuilder(this.cheStarterURL);
    cheServerURLBuilder.append(relativePath);
    cheServerURLBuilder.append('?');
    getDefaultQueryParameters()
        .forEach(
            (key, value) -> cheServerURLBuilder.append(key).append('=').append(value).append('&'));
    cheServerURLBuilder.deleteCharAt(cheServerURLBuilder.lastIndexOf("&"));

    Request request =
        new Request.Builder()
            .url(cheServerURLBuilder.toString())
            .addHeader("Authorization", authorization)
            .patch(body)
            .build();

    try {
      response = client.newCall(request).execute();
      if (response.isSuccessful()) {
        LOG.info(
            "Prepare workspace request send. Starting workspace named: " + workspaceName + ".");
      } else {
        throw new RuntimeException(
            "Workspace failed to start [" + response.code() + "]:" + response.body().string());
      }
    } catch (IOException e) {
      LOG.error("Failed to set environment before workspace start: " + e.getMessage(), e);
      throw e;
    }
  }

  private String getNameFromResponse(HttpJsonResponse response)
      throws RuntimeException, JsonParseException {
    if (response.getResponseCode() == 200) {
      String responseBody = response.asString();
      JsonObject workspaceJsonObject = PARSER.parse(responseBody).getAsJsonObject();
      JsonObject workspaceConfigJsonObject = workspaceJsonObject.get("config").getAsJsonObject();
      return workspaceConfigJsonObject.get("name").getAsString();
    } else {
      String error = "Could not get name from response. Request failed or the response is empty.";
      LOG.error(error);
      throw new RuntimeException(error);
    }
  }

  private HashMap<String, String> getDefaultQueryParameters() {
    HashMap<String, String> queryParameters = new HashMap<>();
    queryParameters.put(
        "masterUrl", this.cheHost); // set by env variables when launching che-starter -- UNUSED
    queryParameters.put(
        "namespace", "sth"); // set by env variables when launching che-starter -- UNUSED
    return queryParameters;
  }
}
