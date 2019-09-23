package com.redhat.che.start_workspace_reporter.model;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

public class HttpRequestWrapperResponse {

  private static final Logger LOG = Logger.getLogger(HttpRequestWrapperResponse.class.getName());
  private static final Gson gson = new Gson();

  private int statusCode;
  private Header contentType;
  private Header encoding;
  private Set<Header> headers;
  private InputStream contentInputStream;

  private String rawData = null;

  public HttpRequestWrapperResponse(HttpResponse response) throws IllegalArgumentException {
    if (response == null) {
      throw new IllegalArgumentException("HttpResponse cannot be null");
    }
    this.statusCode = response.getStatusLine().getStatusCode();
    this.headers = new HashSet<Header>();
    this.headers.addAll(Arrays.asList(response.getAllHeaders()));
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      this.contentType = entity.getContentType();
      this.encoding = entity.getContentEncoding();
      try {
        this.contentInputStream = entity.getContent();
      } catch (IOException e) {
        throw new RuntimeException(
            "Could not get content input stream from HttpResponse:" + e.getLocalizedMessage(), e);
      }
    } else {
      this.contentType = response.getFirstHeader("Content-Type");
      this.encoding = response.getFirstHeader("Content-Encoding");
      this.contentInputStream = null;
    }
  }

  public int getStatusCode() {
    return this.statusCode;
  }

  public String getContentType() {
    return this.contentType != null ? this.contentType.getValue() : "Unknown";
  }

  public String getContentEncoding() {
    return this.encoding != null ? this.encoding.getValue() : "Unknown";
  }

  public Set<Header> getHeaders() {
    return this.headers;
  }

  public InputStream getContentStream() {
    return this.contentInputStream;
  }

  public String grabContent() throws IOException {
    return toJSONRPC(getResponseString()).toString();
  }

  public JSONRPCResponse asJSONRPCResponse() throws IOException {
    return toJSONRPC(getResponseString());
  }

  private String getResponseString() throws IOException {
    if (this.rawData == null) {
      if (this.contentInputStream == null) return null;
      InputStreamReader inputStreamReader = new InputStreamReader(this.contentInputStream);
      BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
      StringBuilder sb = new StringBuilder();
      String buffer;
      while ((buffer = bufferedReader.readLine()) != null) {
        sb.append(buffer);
      }
      this.rawData = sb.toString();
    }
    return rawData;
  }

  private JSONRPCResponse toJSONRPC(String jsonRaw) {
    return gson.fromJson(jsonRaw, JSONRPCResponse.class);
  }
}
