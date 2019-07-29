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
package com.redhat.che.plugin.analytics.wsmaster;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.commons.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/fabric8-che-analytics")
public class AnalyticsService extends Service {
  private static final Logger LOG = LoggerFactory.getLogger(AnalyticsService.class);

  String segmentWriteKey;
  String woopraDomain;

  @Inject
  public AnalyticsService(
      @Nullable @Named("che.fabric8.analytics.segment_write_key") String segmentWriteKey,
      @Nullable @Named("che.fabric8.analytics.woopra_domain") String woopraDomain) {
    this.segmentWriteKey = segmentWriteKey;
    this.woopraDomain = woopraDomain;
  }

  @GET
  @Path("/segment-write-key")
  public String segmentWriteKey() {
    return segmentWriteKey == null ? "" : segmentWriteKey;
  }

  @GET
  @Path("/woopra-domain")
  public String woopraDomain() {
    return woopraDomain == null ? "" : woopraDomain;
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
}
