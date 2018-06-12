/*
 * Copyright (c) 2016-2018 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */

package com.redhat.che.plugin.analytics.wsagent;

import static com.redhat.che.plugin.analytics.wsagent.AnalyticsEvent.*;
import static com.redhat.che.plugin.analytics.wsagent.EventProperties.*;
import static org.slf4j.LoggerFactory.getLogger;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.eclipse.che.commons.subject.Subject;
import org.slf4j.Logger;

/**
 * Filter the API accesses and convert some of them into meaningful events to be consumed by the
 * {@link AnalyticsManager}.
 *
 * <p>Also detects the workspace activity and notifies the {@link AnalyticsManager} about it.
 *
 * @author David Festal
 */
@Singleton
public class UrlToEventFilter implements Filter {
  private static final Logger LOG = getLogger(UrlToEventFilter.class);

  private boolean startWorkspaceEventSent = false;
  private final AnalyticsManager manager;

  @Inject
  public UrlToEventFilter(AnalyticsManager manager) {
    this.manager = manager;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    chain.doFilter(request, response);
    try {
      if (!manager.isEnabled()) {
        return;
      }
      if (!(request instanceof HttpServletRequest)) {
        return;
      }

      HttpServletRequest httpRequest = (HttpServletRequest) request;

      String path = httpRequest.getServletPath();

      if ("/api/liveness".contentEquals(path)) {
        return;
      }

      String ip = httpRequest.getHeader("X-Forwarded-For");
      if (ip == null) {
        ip = httpRequest.getRemoteAddr();
      }

      String userAgent = httpRequest.getHeader("User-Agent");

      String method = httpRequest.getMethod();

      final HttpSession session = httpRequest.getSession();
      Subject subject = (Subject) session.getAttribute("principal");
      if (subject == null) {
        LOG.warn("No Subject to find out a user for analytics");
        return;
      }
      String userId = subject.getUserId();
      if (userId == null) {
        LOG.warn("No userId to find out a user for analytics");
        return;
      }

      synchronized (this) {
        if ("GET".equals(method) && path.startsWith("/api/project-type")) {
          if (!startWorkspaceEventSent) {
            startWorkspaceEventSent = true;
            manager.onEvent(userId, WORKSPACE_STARTED, Collections.emptyMap(), ip, userAgent);
          } else {
            manager.onEvent(userId, WORKSPACE_OPENED, Collections.emptyMap(), ip, userAgent);
          }
          return;
        }

        if (!startWorkspaceEventSent) {
          return;
        }
      }

      if ("PUT".equals(method) && path.startsWith("/api/project/file")) {
        String language = guessLanguage(path);
        manager.onEvent(
            userId,
            EDITOR_USED,
            ImmutableMap.<String, Object>builder().put(PROGRAMMING_LANGUAGE, language).build(),
            ip,
            userAgent);
      }

      manager.onActivity(userId);
    } catch (Exception e) {
      LOG.error("", e);
    }
  }

  private String guessLanguage(String path) {
    String language;
    if (path.endsWith(".xml")) {
      return "xml";
    }
    if (path.endsWith(".js")) {
      return "javascript";
    }
    if (path.endsWith(".jsp")) {
      return "jsp";
    }
    String extension = "";
    String fileName = path;
    int lastSlash = path.lastIndexOf('/');
    if (lastSlash >= 0) {
      fileName = path.substring(lastSlash + 1);
    }
    int lastPoint = fileName.lastIndexOf('.');
    if (lastPoint > 0) {
      extension = fileName.substring(lastPoint + 1);
    }
    if (extension.isEmpty()) {
      language = "unknown";
    } else {
      language = "unknown : ." + extension;
    }
    return language;
  }

  @Override
  public void destroy() {}
}
