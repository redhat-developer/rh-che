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

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.eclipse.che.api.core.model.workspace.WorkspaceStatus.RUNNING;
import static org.eclipse.che.multiuser.machine.authentication.shared.Constants.MACHINE_TOKEN_KIND;
import static org.eclipse.che.multiuser.machine.authentication.shared.Constants.USER_ID_CLAIM;

import com.google.inject.Singleton;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriBuilder;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.workspace.Runtime;
import org.eclipse.che.api.core.model.workspace.WorkspaceStatus;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.workspace.server.WorkspaceManager;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.server.token.MachineTokenProvider;
import org.eclipse.che.api.workspace.shared.dto.event.WorkspaceStatusEvent;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.multiuser.api.authentication.commons.token.RequestTokenExtractor;
import org.eclipse.che.multiuser.machine.authentication.server.signature.SignatureKeyManager;
import org.eclipse.che.multiuser.machine.authentication.server.signature.SignatureKeyManagerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filter that forwards, to the WSAgent, workspaces-related events that are relevant for the WsAgent
 * AnalyticsManager (telemetry).
 *
 * @author David Festal
 */
@Singleton
public class ForwardActivityFilter implements Filter, EventSubscriber<WorkspaceStatusEvent> {
  private static final Logger LOG = LoggerFactory.getLogger(ForwardActivityFilter.class);

  private final WorkspaceManager workspaceManager;
  private final MachineTokenProvider machineTokenProvider;
  private final RequestTokenExtractor tokenExtractor;
  private final SignatureKeyManager keyManager;

  @Inject
  public ForwardActivityFilter(
      WorkspaceManager workspaceManager,
      MachineTokenProvider machineTokenProvider,
      RequestTokenExtractor tokenExtractor,
      SignatureKeyManager keyManager) {
    this.workspaceManager = workspaceManager;
    this.machineTokenProvider = machineTokenProvider;
    this.tokenExtractor = tokenExtractor;
    this.keyManager = keyManager;
  }

  @Inject
  void subscribe(EventService eventService) {
    eventService.subscribe(this);
  }

  @Override
  public void onEvent(WorkspaceStatusEvent event) {
    String workspaceId = event.getWorkspaceId();
    if (WorkspaceStatus.STOPPING.equals(event.getStatus())) {
      try {
        final WorkspaceImpl workspace = workspaceManager.getWorkspace(workspaceId);
        Runtime runtime = workspace.getRuntime();
        if (runtime != null) {
          String userId = runtime.getOwner();
          callWorkspaceAnalyticsEndpoint(
              userId, workspaceId, "/fabric8-che-analytics/stopped", "notify stop", workspace);
        } else {
          LOG.warn(
              "Received stopping event for workspace {}/{} with id {} but runtime is null",
              workspace.getNamespace(),
              workspace.getConfig().getName(),
              workspaceId);
        }
      } catch (NotFoundException | ServerException e) {
        LOG.warn("", e);
        return;
      }
    }
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  private void forwardActivity(ServletRequest request) {
    if (!(request instanceof HttpServletRequest)) {
      return;
    }
    HttpServletRequest httpRequest = (HttpServletRequest) request;

    try {
      String[] pathParts = httpRequest.getServletPath().split("/");
      if (pathParts.length <= 1) {
        return;
      }

      String workspaceId = pathParts[pathParts.length - 1];
      if (workspaceId == null) {
        return;
      }

      String userId = extractUserId(httpRequest, workspaceId);

      try {
        final WorkspaceImpl workspace = workspaceManager.getWorkspace(workspaceId);
        if (workspace.getStatus() == RUNNING) {
          callWorkspaceAnalyticsEndpoint(
              userId,
              workspaceId,
              "/fabric8-che-analytics/activity",
              "forward activity",
              workspace);
        } else {
          LOG.debug(
              "In Forward Activity for path: {} - workspace {} is NOT RUNNING",
              httpRequest.getServletPath(),
              workspaceId);
        }
      } catch (NotFoundException e) {
        LOG.warn(
            "In Forward Activity for path: {} - workspace {} is NOT FOUND",
            httpRequest.getServletPath(),
            workspaceId);
      }
    } catch (Exception e) {
      LOG.error("Cannot forward activity to the wsagent for analytics", e);
    }
  }

  private void callWorkspaceAnalyticsEndpoint(
      String userId,
      String workspaceId,
      String targetEndpoint,
      String action,
      final WorkspaceImpl workspace) {
    workspace
        .getRuntime()
        .getMachines()
        .values()
        .stream()
        .flatMap(machine -> machine.getServers().entrySet().stream())
        .filter(entry -> "wsagent/http".equals(entry.getKey()))
        .map(entry -> entry.getValue())
        .forEach(
            server -> {
              try {
                URI uri = UriBuilder.fromUri(server.getUrl()).path(targetEndpoint).build();
                LOG.debug("{} on workspace {} to {} for user {}", action, workspaceId, uri, userId);
                HttpURLConnection httpURLConnection =
                    (HttpURLConnection) uri.toURL().openConnection();
                httpURLConnection.setRequestProperty(
                    HttpHeaders.AUTHORIZATION, machineTokenProvider.getToken(userId, workspaceId));
                int responseCode = httpURLConnection.getResponseCode();
                if (responseCode != 200) {
                  LOG.error(
                      "Cannot {} to the wsagent for analytics: response code = {}",
                      action,
                      responseCode);
                }
              } catch (Exception e) {
                LOG.error("Cannot " + action + " to the wsagent for analytics", e);
              }
            });
  }

  private String extractUserId(HttpServletRequest httpRequest, String workspaceId) {
    // First search in the session fro activity notification coming from the client

    final HttpSession session = httpRequest.getSession();

    Subject subject = (Subject) session.getAttribute("che_subject");
    if (subject != null) {
      String userId = subject.getUserId();
      if (userId != null) {
        return userId;
      }
    }

    // Then search in the machine token for activity notification coming from the agents

    final String token = tokenExtractor.getToken(httpRequest);

    if (isNullOrEmpty(token)) {
      return null;
    }

    // check token signature and verify is this token machine or not
    try {
      final Jws<Claims> jwt =
          Jwts.parser()
              .setSigningKey(keyManager.getOrCreateKeyPair(workspaceId).getPublic())
              .parseClaimsJws(token);
      final Claims claims = jwt.getBody();

      if (MACHINE_TOKEN_KIND.equals(jwt.getHeader().get("kind"))) {
        return claims.get(USER_ID_CLAIM, String.class);
      }
    } catch (UnsupportedJwtException
        | MalformedJwtException
        | SignatureException
        | SignatureKeyManagerException
        | ExpiredJwtException
        | IllegalArgumentException ex) {
      LOG.warn("Could not get a user Id from a machine token", ex);
    }
    return null;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    chain.doFilter(request, response);
    forwardActivity(request);
  }

  @Override
  public void destroy() {}
}
