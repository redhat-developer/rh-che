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

import static com.google.common.collect.ImmutableMap.of;
import static com.redhat.che.plugin.analytics.wsagent.AnalyticsEvent.EDITOR_USED;
import static com.redhat.che.plugin.analytics.wsagent.AnalyticsEvent.WORKSPACE_OPENED;
import static com.redhat.che.plugin.analytics.wsagent.EventProperties.PROGRAMMING_LANGUAGE;
import static java.util.Collections.emptyMap;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.eclipse.che.commons.subject.Subject;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class UrlToEventFilterTest {

  private static String USER_ID = "theUserId";
  private static String FORWARDED_FOR = "theForwardedForHeader";
  private static String USER_AGENT = "theUserAgent";
  private static String REMOTE_ADDRESS = "theRemoteAddress";

  @Mock private AnalyticsManager analyticsManager;
  @Mock private FilterChain filterChain;
  @Mock private HttpServletRequest request;
  @Mock private HttpSession session;
  @Mock private Subject subject;
  @Mock private ServletRequest servletRequest;
  @Mock private ServletResponse response;

  private UrlToEventFilter filter;

  @BeforeMethod
  public void setUp() throws Exception {
    when(analyticsManager.isEnabled()).thenReturn(true);
    when(request.getSession()).thenReturn(session);
    when(request.getHeader("X-Forwarded-For")).thenReturn(FORWARDED_FOR);
    when(request.getRemoteAddr()).thenReturn(REMOTE_ADDRESS);
    when(request.getHeader("User-Agent")).thenReturn(USER_AGENT);
    when(session.getAttribute("principal")).thenReturn(subject);
    when(subject.getUserId()).thenReturn(USER_ID);
    when(request.getServletPath()).thenReturn("anything");
    when(request.getMethod()).thenReturn("anything");

    filter = new UrlToEventFilter(analyticsManager);
    filter.startWorkspaceEventSent = true;
  }

  @Test
  public void doNothingIfAnalyticsDisabled() throws Exception {
    when(analyticsManager.isEnabled()).thenReturn(false);

    filter.doFilter(request, response, filterChain);

    InOrder inOrder = inOrder(filterChain, analyticsManager);
    inOrder.verify(filterChain).doFilter(request, response);
    inOrder.verify(analyticsManager).isEnabled();
    verifyNoMoreInteractions(analyticsManager, filterChain, request, servletRequest, response);
  }

  @Test
  public void doNothingIfNotHttpServletRequest() throws Exception {

    filter.doFilter(servletRequest, response, filterChain);

    InOrder inOrder = inOrder(filterChain, analyticsManager);
    inOrder.verify(filterChain).doFilter(servletRequest, response);
    inOrder.verify(analyticsManager).isEnabled();
    verifyNoMoreInteractions(analyticsManager, filterChain, request, servletRequest, response);
  }

  @Test
  public void doNothingIfLivenessEndpointCalled() throws Exception {
    when(request.getServletPath()).thenReturn("/api/liveness");

    filter.doFilter(request, response, filterChain);

    InOrder inOrder = inOrder(filterChain, analyticsManager, request);
    inOrder.verify(filterChain).doFilter(request, response);
    inOrder.verify(analyticsManager).isEnabled();
    verifyNoMoreInteractions(analyticsManager, filterChain);
  }

  @Test
  public void doNothingIfNoSubject() throws Exception {
    when(session.getAttribute("principal")).thenReturn(null);

    filter.doFilter(request, response, filterChain);

    InOrder inOrder = inOrder(filterChain, analyticsManager, request);
    inOrder.verify(filterChain).doFilter(request, response);
    inOrder.verify(analyticsManager).isEnabled();
    verifyNoMoreInteractions(analyticsManager, filterChain);
  }

  @Test
  public void doNothingIfNoUserId() throws Exception {
    when(subject.getUserId()).thenReturn(null);

    filter.doFilter(request, response, filterChain);

    InOrder inOrder = inOrder(filterChain, analyticsManager, request);
    inOrder.verify(filterChain).doFilter(request, response);
    inOrder.verify(analyticsManager).isEnabled();
    verifyNoMoreInteractions(analyticsManager, filterChain);
  }

  @Test
  public void doNothingBeforeStartWorkspaceEvent() throws Exception {

    filter.startWorkspaceEventSent = false;
    filter.doFilter(request, response, filterChain);

    InOrder inOrder = inOrder(filterChain, analyticsManager, request);
    inOrder.verify(filterChain).doFilter(request, response);
    inOrder.verify(analyticsManager).isEnabled();
    verifyNoMoreInteractions(analyticsManager, filterChain);
  }

  @Test
  public void doEnableAfterStartWorkspaceEvent() throws Exception {
    when(request.getServletPath()).thenReturn("/api/project-type");
    when(request.getMethod()).thenReturn("GET");

    filter.startWorkspaceEventSent = false;
    filter.doFilter(request, response, filterChain);

    InOrder inOrder = inOrder(filterChain, analyticsManager, request);
    inOrder.verify(filterChain).doFilter(request, response);
    inOrder.verify(analyticsManager).isEnabled();

    verify(analyticsManager)
        .onEvent(USER_ID, AnalyticsEvent.WORKSPACE_STARTED, emptyMap(), FORWARDED_FOR, USER_AGENT);
    assertEquals(filter.startWorkspaceEventSent, true);
    verifyNoMoreInteractions(analyticsManager, filterChain);
  }

  @Test
  public void detectOpenWorkspaceEventWhenAlreadyStarted() throws Exception {
    when(request.getServletPath()).thenReturn("/api/project-type");
    when(request.getMethod()).thenReturn("GET");

    filter.doFilter(request, response, filterChain);

    InOrder inOrder = inOrder(filterChain, analyticsManager, request);
    inOrder.verify(filterChain).doFilter(request, response);
    inOrder.verify(analyticsManager).isEnabled();

    verify(analyticsManager)
        .onEvent(USER_ID, WORKSPACE_OPENED, emptyMap(), FORWARDED_FOR, USER_AGENT);
    assertEquals(filter.startWorkspaceEventSent, true);
    verifyNoMoreInteractions(analyticsManager, filterChain);
  }

  @Test
  public void useRemoteAddrWhenNoForwardedForHeader() throws Exception {
    when(request.getServletPath()).thenReturn("/api/project-type");
    when(request.getMethod()).thenReturn("GET");
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);

    filter.doFilter(request, response, filterChain);

    verify(analyticsManager)
        .onEvent(USER_ID, WORKSPACE_OPENED, emptyMap(), REMOTE_ADDRESS, USER_AGENT);
  }

  @Test
  public void reportActivityOnAnyActionWhenAlreadyStarted() throws Exception {

    filter.doFilter(request, response, filterChain);

    InOrder inOrder = inOrder(filterChain, analyticsManager, request);
    inOrder.verify(filterChain).doFilter(request, response);
    inOrder.verify(analyticsManager).isEnabled();

    verify(analyticsManager).onActivity(USER_ID);
  }

  @Test
  public void sendEditFileEventAndReportActivity() throws Exception {
    when(request.getServletPath()).thenReturn("/api/project/file/file.java");
    when(request.getMethod()).thenReturn("PUT");

    filter.doFilter(request, response, filterChain);

    InOrder inOrder = inOrder(filterChain, analyticsManager, request);
    inOrder.verify(filterChain).doFilter(request, response);
    inOrder.verify(analyticsManager).isEnabled();

    verify(analyticsManager)
        .onEvent(USER_ID, EDITOR_USED, of(PROGRAMMING_LANGUAGE, "java"), FORWARDED_FOR, USER_AGENT);
    verify(analyticsManager).onActivity(USER_ID);
  }

  @Test
  public void languageDetection() throws Exception {
    assertEquals(filter.guessLanguage("/path/file.whatIsIt"), "unknown : .whatIsIt");
    assertEquals(filter.guessLanguage("/path/file"), "unknown");
    assertEquals(filter.guessLanguage("file.java"), "java");
  }
}
