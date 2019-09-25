/*
 * Copyright (c) 2018-2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.che.start_workspace_reporter.util;

import java.io.IOException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

public class HttpRequestWrapper {

  private HttpClient client;
  private String baseURL;

  public HttpRequestWrapper(String baseURL) {
    this.client = HttpClientBuilder.create().build();
    this.baseURL = baseURL;
  }

  public HttpResponse get() throws IOException {
    return this.get("");
  }

  public HttpResponse get(String relativePath) throws IOException {
    HttpGet getRequest = new HttpGet(baseURL + relativePath);
    return this.client.execute(getRequest);
  }

  public HttpResponse post(String relativePath, final String contentType, final String content)
      throws IOException {
    HttpPost postRequest = new HttpPost(this.baseURL + relativePath);
    postRequest.setEntity(new StringEntity(content));
    postRequest.setHeader(HttpHeaders.CONTENT_TYPE, contentType);
    return client.execute(postRequest);
  }
}
