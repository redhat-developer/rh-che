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

import static org.mockito.Mockito.doReturn;
import static org.testng.Assert.assertEquals;

import javax.servlet.http.HttpServletRequest;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class End2EndFlowServiceTest {

  private static String CLIENT_IP = "theClientIP";

  @Mock private HttpServletRequest servletRequest;

  private End2EndFlowService service;

  @BeforeMethod
  public void setUp() throws Exception {
    service = new End2EndFlowService("key", "key", true);
  }

  @Test
  public void getClientIpFromXForwardedForWithSingleValue() throws Exception {
    doReturn(CLIENT_IP).when(servletRequest).getHeader("X-Forwarded-For");
    doReturn(null).when(servletRequest).getHeader("Forwarded");

    String clientIP = service.retrieveClientIp(servletRequest);

    assertEquals(clientIP, CLIENT_IP);
  }

  @Test
  public void getClientIpFromXForwardedForWithSeveralValues() throws Exception {
    doReturn(CLIENT_IP + " , otherIPs").when(servletRequest).getHeader("X-Forwarded-For");
    doReturn(null).when(servletRequest).getHeader("Forwarded");

    String clientIP = service.retrieveClientIp(servletRequest);

    assertEquals(clientIP, CLIENT_IP);
  }

  @Test
  public void getClientIpFromForwardedWithSingleValue() throws Exception {
    doReturn(null).when(servletRequest).getHeader("X-Forwarded-For");
    doReturn("for=" + CLIENT_IP + ";host=anyhost;proto=https")
        .when(servletRequest)
        .getHeader("Forwarded");

    String clientIP = service.retrieveClientIp(servletRequest);

    assertEquals(clientIP, CLIENT_IP);
  }

  @Test
  public void getClientIpFromForwardedWithSingleValueWithSpaces() throws Exception {
    doReturn(null).when(servletRequest).getHeader("X-Forwarded-For");
    doReturn("for=" + CLIENT_IP + " ; host=anyhost;proto=https")
        .when(servletRequest)
        .getHeader("Forwarded");

    String clientIP = service.retrieveClientIp(servletRequest);

    assertEquals(clientIP, CLIENT_IP);
  }

  @Test
  public void getClientIpFromForwardedWithSeveralValues() throws Exception {
    doReturn(null).when(servletRequest).getHeader("X-Forwarded-For");
    doReturn(
            "for=" + CLIENT_IP + ";host=anyhost;proto=https , for=otherIP;host=anyhost;proto=https")
        .when(servletRequest)
        .getHeader("Forwarded");

    String clientIP = service.retrieveClientIp(servletRequest);

    assertEquals(clientIP, CLIENT_IP);
  }

  @Test
  public void getClientIpFromXForwardedFirst() throws Exception {
    doReturn(CLIENT_IP).when(servletRequest).getHeader("X-Forwarded-For");
    doReturn("for=otherIP;host=anyhost;proto=https").when(servletRequest).getHeader("Forwarded");
    doReturn("otherIP").when(servletRequest).getRemoteAddr();

    String clientIP = service.retrieveClientIp(servletRequest);

    assertEquals(clientIP, CLIENT_IP);
  }

  @Test
  public void getClientIpFromForwardedIfNotXForwardedFor() throws Exception {
    doReturn(null).when(servletRequest).getHeader("X-Forwarded-For");
    doReturn("for=" + CLIENT_IP + ";host=anyhost;proto=https")
        .when(servletRequest)
        .getHeader("Forwarded");

    String clientIP = service.retrieveClientIp(servletRequest);

    assertEquals(clientIP, CLIENT_IP);
  }

  @Test
  public void getClientIpFromRemoteAddrByDefault() throws Exception {
    doReturn(null).when(servletRequest).getHeader("X-Forwarded-For");
    doReturn(null).when(servletRequest).getHeader("Forwarded");
    doReturn(CLIENT_IP).when(servletRequest).getRemoteAddr();

    String clientIP = service.retrieveClientIp(servletRequest);

    assertEquals(clientIP, CLIENT_IP);
  }
}
