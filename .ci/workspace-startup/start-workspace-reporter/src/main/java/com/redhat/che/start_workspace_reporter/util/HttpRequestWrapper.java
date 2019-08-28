package com.redhat.che.start_workspace_reporter.util;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;

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

    public HttpResponse post(String relativePath, final String contentType, final String content) throws IOException {
        HttpPost postRequest = new HttpPost(this.baseURL + relativePath);
        postRequest.setEntity(new StringEntity(content));
        postRequest.setHeader(HttpHeaders.CONTENT_TYPE, contentType);
        return client.execute(postRequest);
    }

}
