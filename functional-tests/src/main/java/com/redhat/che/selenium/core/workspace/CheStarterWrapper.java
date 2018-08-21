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
import com.google.inject.name.Named;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author Katerina Kanova (kkanova) */
public class CheStarterWrapper {

  private static final Logger LOG = LoggerFactory.getLogger(CheStarterWrapper.class);

  private String host;

  @Inject(optional = true)
  @Named("sys.cheStarterUrl")
  private String cheStarterURL = "http://localhost:10000";

  @Inject
  public CheStarterWrapper(@Named("che.host") String cheHost) {
    this.host = cheHost;
  }
  /** Checks whether che-starter is already running. Throws RuntimeException otherwise. */
  public void checkIsRunning() {
    Builder requestBuilder = new Request.Builder().url(this.cheStarterURL);
    Request livenessRequest = requestBuilder.get().build();
    OkHttpClient client = new OkHttpClient();
    Response livenessResponse;
    try {
      livenessResponse = client.newCall(livenessRequest).execute();
      if (livenessResponse.code() != 200) {
        String errMsg =
            "Liveness probe for che-starter failed with HTTP code: "
                + livenessResponse.code()
                + ". It is probably not running";
        LOG.error(errMsg);
        throw new RuntimeException(errMsg);
      }
    } catch (IOException e) {
      String errMsg = "Liveness probe for che-starter failed.";
      LOG.error(errMsg, e);
      throw new RuntimeException(errMsg, e);
    }
  }

  public String createWorkspace(String pathToJson, String token) throws Exception {
    BufferedReader buffer = null;
    try {
      buffer =
          new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(pathToJson)));
    } catch (Exception e) {
      LOG.error("File with json was not found on address: " + pathToJson, e);
      throw e;
    }
    String json = buffer.lines().collect(Collectors.joining("\n"));
    String path = "/workspace";
    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(JSON, json);
    StringBuilder sb =
        new StringBuilder(this.cheStarterURL)
            .append(path)
            .append("?")
            .append("masterUrl=")
            .append(this.host)
            .append("&")
            .append("namespace=sth");
    Builder requestBuilder = new Request.Builder().url(sb.toString());
    requestBuilder.addHeader("Content-Type", "application/json");
    requestBuilder.addHeader("Authorization", "Bearer " + token);
    Request request = requestBuilder.post(body).build();
    OkHttpClient client = new OkHttpClient();
    try {
      Response response = client.newCall(request).execute();
      return getNameFromResponse(response);
    } catch (IOException e) {
      LOG.error("Workspace could not be created : " + e.getMessage(), e);
      return null;
    }
  }

  public boolean deleteWorkspace(String workspaceName, String token) {
    StringBuilder sb = new StringBuilder(this.cheStarterURL);
    String path = "/workspace/" + workspaceName;
    sb.append(path);
    sb.append("?");
    sb.append("masterUrl=").append(this.host).append("&").append("namespace=sth");
    Builder requestBuilder = new Request.Builder().url(sb.toString());
    requestBuilder.addHeader("Content-Type", "application/json");
    requestBuilder.addHeader("Authorization", "Bearer " + token);
    OkHttpClient client = new OkHttpClient();
    try {
      Response response = client.newCall(requestBuilder.delete().build()).execute();
      LOG.info("Workspace delete response : " + response.message());
      if (!response.isSuccessful()) {
        if (response.message().equals("Workspace not found")) {
          LOG.warn(
              "Workspace could not be deleteded because workspace is not found. Continuing tests.");
          return true;
        }
      }
      return response.isSuccessful();
    } catch (IOException e) {
      LOG.error("Workspace could not be deleted : " + e.getMessage(), e);
      return false;
    }
  }

  public void startWorkspace(String WorkspaceID, String name, String token) throws Exception {
    OkHttpClient client = new OkHttpClient();
    String path = "/workspace/" + name;
    StringBuilder sb = new StringBuilder(this.cheStarterURL);
    sb.append(path);
    sb.append("?");
    sb.append("masterUrl=").append(this.host).append("&").append("namespace=sthf");
    Builder requestBuilder = new Request.Builder().url(sb.toString());
    requestBuilder.addHeader("Authorization", "Bearer " + token);
    RequestBody body = RequestBody.create(null, new byte[0]);
    Request request = requestBuilder.patch(body).build();
    try {
      Response response = client.newCall(request).execute();
      if (response.isSuccessful()) {
        LOG.info("Prepare workspace request send. Starting workspace.");
        sb = new StringBuilder("https://" + this.host);
        sb.append("/api/workspace/");
        sb.append(WorkspaceID);
        sb.append("/runtime");
        requestBuilder = new Request.Builder().url(sb.toString());
        requestBuilder.addHeader("Authorization", "Bearer " + token);
        request = requestBuilder.post(body).build();
        response = client.newCall(request).execute();
        if (response.isSuccessful()) {
          LOG.info("Workspace was started. Waiting until workspace is running.");
        }
      }
    } catch (IOException e) {
      LOG.error("Workspace start failed : " + e.getMessage(), e);
      throw e;
    }
  }

  private String getNameFromResponse(Response response) {
    try {
      String responseString = response.body().string();
      Object jsonDocument =
          Configuration.defaultConfiguration().jsonProvider().parse(responseString);
      return JsonPath.read(jsonDocument, "$.config.name");
    } catch (IOException e) {
      LOG.error(e.getLocalizedMessage());
      e.printStackTrace();
    }
    return null;
  }
}
