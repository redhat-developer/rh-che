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
package com.redhat.che.end2end;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.HttpsURLConnection;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.lang.IoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Path("fabric8-end2end")
public class End2EndFlowService extends Service {
  private static final Logger LOG = LoggerFactory.getLogger(End2EndFlowService.class);

  private final String reCaptchaSiteKey;
  private final String reCaptchaSecretKey;
  private final boolean verifyWithIp;

  private final Map<String, Function<String, String>> staticFilesFilters;

  @Inject
  public End2EndFlowService(
      @Nullable @Named("che.fabric8.end2end.protect.site_key") String siteKey,
      @Nullable @Named("che.fabric8.end2end.protect.secret_key") String secretKey,
      @Named("che.fabric8.end2end.protect.verify_with_ip") boolean verifyWithIp) {
    this.verifyWithIp = verifyWithIp;
    this.reCaptchaSiteKey = siteKey;
    this.reCaptchaSecretKey = secretKey;
    this.staticFilesFilters = new HashMap<>();
    String siteKeyDecl;
    if (siteKey != null && !siteKey.isEmpty()) {
      siteKeyDecl = "var siteKey='" + siteKey + "';";
    } else {
      siteKeyDecl = "var siteKey='';";
      LOG.warn("No ReCaptcha site key was provided. ReCaptcha user verification is disabled !");
    }
    staticFilesFilters.put(
        "files/provision.html", line -> line.replace("const siteKey;", siteKeyDecl));
  }

  @GET
  @Path("site-key")
  public String siteKey() {
    return reCaptchaSiteKey == null ? "" : reCaptchaSiteKey;
  }

  @POST
  @Path("verify")
  @Consumes("text/plain")
  @Produces(APPLICATION_JSON)
  public Response verifyReCaptchaToken(String token, @Context HttpServletRequest servletRequest) {
    int responseCode = 500;
    try {
      URL obj = new URL("https://www.google.com/recaptcha/api/siteverify");
      HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

      con.setRequestMethod("POST");

      String ip = retrieveClientIp(servletRequest);

      String remoteIpPart = "";

      if (ip != null) {
        LOG.info("Starting reCaptcha verification for user from IP: {}", ip);
        if (verifyWithIp) {
          try {
            InetAddress address = InetAddress.getByName(ip);
            if (!address.isSiteLocalAddress()) {
              LOG.info("Including client IP in the reCaptcha verification");
              remoteIpPart = "&remoteip=" + address.getHostAddress();
            } else {
              LOG.warn(
                  "Client IP cannot be used in the reCaptcha verification since it is a private IP: {}",
                  ip);
            }
          } catch (UnknownHostException e) {
            LOG.warn(
                "Client IP cannot be used in the reCaptcha verification since it's invalid: {}",
                ip);
          }
        }
      }

      String urlParameters = "secret=" + reCaptchaSecretKey + "&response=" + token + remoteIpPart;

      con.setDoOutput(true);
      try (DataOutputStream wr = new DataOutputStream(con.getOutputStream())) {
        wr.writeBytes(urlParameters);
        wr.flush();
      }

      responseCode = con.getResponseCode();
      String response = IoUtil.readAndCloseQuietly(con.getInputStream());
      if (responseCode == 200) {
        return Response.ok(response).build();
      } else {
        LOG.error(
            "reCaptcha verification failed with the following response code: {} - {}",
            responseCode,
            response);
      }
    } catch (IOException e) {
      LOG.error("Captcha verification failed with an exception", e);
    }

    return Response.status(responseCode).build();
  }

  @VisibleForTesting
  String retrieveClientIp(HttpServletRequest servletRequest) {
    String ip = null;
    String xForwardedFor = servletRequest.getHeader("X-Forwarded-For");
    String forwarded = servletRequest.getHeader("Forwarded");
    if (xForwardedFor != null) {
      LOG.info(
          "Looking for reCaptcha verification user IP into the request `X-Forwarded-For` header");
      int index = xForwardedFor.indexOf(',');
      if (index > 0) {
        xForwardedFor = xForwardedFor.substring(0, index).trim();
      }
      ip = xForwardedFor;
    } else if (forwarded != null) {
      LOG.info("Looking for reCaptcha verification user IP into the request `Forwarded` header");
      int index = forwarded.indexOf(',');
      if (index > 0) {
        forwarded = forwarded.substring(0, index).trim();
      }
      for (String part : forwarded.split(";")) {
        part = part.trim();
        if (part.startsWith("for=")) {
          ip = part.substring(4);
          break;
        }
      }
    }
    if (ip == null) {
      LOG.info("Looking for reCaptcha verification user IP into the request remote address");
      ip = servletRequest.getRemoteAddr();
    }
    return ip;
  }

  private String getLog(String message, HttpServletRequest servletRequest) {
    String ip = servletRequest.getHeader("X-Forwarded-For");
    if (ip == null) {
      ip = servletRequest.getRemoteAddr();
    }

    return new StringBuffer("[E2E Registration Flow - IP = ")
        .append(ip == null ? "unknown" : ip)
        .append("] ")
        .append(message)
        .toString();
  }

  @POST
  @Path("warning")
  @Consumes("text/plain")
  public void warning(String message, @Context HttpServletRequest servletRequest) {
    LOG.warn(getLog(message, servletRequest));
  }

  @POST
  @Path("error")
  @Consumes("text/plain")
  public void error(String message, @Context HttpServletRequest servletRequest) {
    LOG.error(getLog(message, servletRequest));
  }

  @GET
  @Path("{path: files\\/.*}")
  public Response staticResources(@PathParam("path") final String path) {
    if (path.contains("../")) {
      return Response.noContent().build();
    }
    URL resource = Thread.currentThread().getContextClassLoader().getResource("end2end/" + path);
    MediaType mediaType;
    if (path.endsWith(".js")) {
      mediaType = new MediaType("text", "javascript");
    }
    if (path.endsWith(".html")) {
      mediaType = MediaType.TEXT_HTML_TYPE;
    } else {
      mediaType = MediaType.TEXT_PLAIN_TYPE;
    }

    if (resource != null) {
      StreamingOutput stream =
          new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
              URLConnection conn;
              try {
                conn = resource.openConnection();
                try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(conn.getInputStream())); ) {
                  Writer writer = new BufferedWriter(new OutputStreamWriter(os));

                  Stream<String> lines = reader.lines();
                  if (staticFilesFilters.containsKey(path)) {
                    lines = lines.map(staticFilesFilters.get(path));
                  }
                  try {
                    lines.forEach(
                        line -> {
                          try {
                            writer.write(line + "\n");
                            writer.flush();
                          } catch (IOException e) {
                            throw new UncheckedIOException(e);
                          }
                        });
                  } catch (UncheckedIOException e) {
                    if (e.getCause() != null) {
                      throw e.getCause();
                    } else {
                      throw new IOException(e);
                    }
                  }
                }
              } catch (IOException e) {
                String message = "Exception occured during static resource retrieval";
                LOG.error(message, e);
                throw e;
              }
            }
          };

      CacheControl cacheControl = new CacheControl();
      cacheControl.getCacheExtension().put("max-age", "120");
      return Response.ok(stream, mediaType).cacheControl(cacheControl).build();
    }

    return Response.status(Status.NOT_FOUND).build();
  }
}
