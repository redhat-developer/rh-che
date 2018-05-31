/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.EmbeddedMaven;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Katerina Kanova (kkanova)
 */
public class CheStarterWrapper {

  private static final Logger LOG = LoggerFactory.getLogger(CheStarterWrapper.class);

  private String host;
  private String osioUrlPart;
  private String cheStarterURL = "http://localhost:10000";

  @Inject
  public CheStarterWrapper(
      @Named("che.osio.url") String osioUrlPart,
      @Named("che.host") String cheHost,
      @Named("che.chromedriver.port") String chromedriverPort
  ) throws IOException, InterruptedException {
    this.host = cheHost;
    this.osioUrlPart = osioUrlPart;
    /* RUN CHROMEDRIVER */
    String chromeDriverCheckCommand =
        "lsof -i TCP | grep -q 'localhost:" + chromedriverPort + " (LISTEN)'";
    Process chromeDriverCheck = Runtime.getRuntime().exec(chromeDriverCheckCommand);
    chromeDriverCheck.waitFor();
    if (chromeDriverCheck.exitValue() != 0) {
      try {
        Process chromedriver = Runtime.getRuntime().exec("chromedriver");
        LOG.info("Chromedriver successfully started.");
      } catch (IOException e) {
        LOG.error("Could not start process chromedriver:" + e.getMessage());
        throw e;
      }
    }
  }

  public void start() throws IllegalStateException {
    //TODO: Check if che starter is running;
    try {
      File cheStarterDir =
          new File(System.getProperty("user.dir"), "target" + File.separator + "che-starter");

      cloneGitDirectory(cheStarterDir);

      LOG.info("Running che starter.");
      Properties props = new Properties();
      props.setProperty(
          "OPENSHIFT_TOKEN_URL",
          "https://sso." + this.osioUrlPart + "/auth/realms/fabric8/broker/openshift-v3/token");
      props.setProperty(
          "GITHUB_TOKEN_URL",
          "https://auth." + this.osioUrlPart + "/api/token?for=https://github.com");
      props.setProperty(
          "CHE_SERVER_URL", "https://rhche." + this.osioUrlPart);
      String pom = cheStarterDir.getAbsolutePath() + File.separator + "pom.xml";
      EmbeddedMaven.forProject(pom)
          .useMaven3Version("3.5.2")
          .setGoals("spring-boot:run")
          .setProperties(props)
          .useAsDaemon()
          .withWaitUntilOutputLineMathes(".*Started Application in.*", 10, TimeUnit.MINUTES)
          .build();

    } catch (GitAPIException e) {
      throw new IllegalStateException(
          "There was a problem with getting the git che-starter repository", e);
    } catch (TimeoutException e) {
      throw new IllegalStateException("The che-starter haven't started within 300 seconds.", e);
    }
  }

  public String createWorkspace(String pathToJson, String token) throws Exception {
    BufferedReader buffer = null;
    try {
      buffer = new BufferedReader(
          new InputStreamReader(getClass().getResourceAsStream(pathToJson)));
    } catch (Exception e) {
      LOG.error("File with json was not found on address: " + pathToJson, e);
      throw e;
    }
    String json = buffer.lines().collect(Collectors.joining("\n"));
    String path = "/workspace";
    MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    RequestBody body = RequestBody.create(JSON, json);
    StringBuilder sb = new StringBuilder(this.cheStarterURL)
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
      LOG.error("Workspace could not be created : "+e.getMessage(), e);
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
      LOG.info("Workspace delete response : "+response.message());
      return response.isSuccessful();
    } catch (IOException e) {
      LOG.error("Workspace could not be deleted : "+e.getMessage(), e);
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
      LOG.error("Workspace start failed : "+e.getMessage(), e);
      throw e;
    }
  }

  // ================= //
  //  PRIVATE METHODS  //
  // ================= //

  private String getNameFromResponse(Response response) {
    try {
      String responseString = response.body().string();
      Object jsonDocument = Configuration.defaultConfiguration().jsonProvider()
          .parse(responseString);
      return JsonPath.read(jsonDocument, "$.config.name");
    } catch (IOException e) {
      LOG.error(e.getLocalizedMessage());
      e.printStackTrace();
    }
    return null;
  }

  private void cloneGitDirectory(File cheStarterDir) throws GitAPIException {
    LOG.info("Cloning che-starter project.");
    try {
      Git.cloneRepository()
          .setURI("https://github.com/redhat-developer/che-starter")
          .setDirectory(cheStarterDir)
          .call();
    } catch (JGitInternalException ex) {
      // repository already cloned. Do nothing.
    }
  }

}
