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
package com.redhat.che.docs;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.UriBuilder;

@Singleton
public class DocsFilter implements Filter {
  private static Pattern docsPattern = Pattern.compile("/docs(.*)");
  private final String docsRedirectUrl;

  @Inject
  public DocsFilter(@Named("che.fabric8.docs.url") String docsRedirectUrl) {
    this.docsRedirectUrl = docsRedirectUrl;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse resp = (HttpServletResponse) response;

    if ("GET".equals(req.getMethod())) {
      Matcher matcher = docsPattern.matcher(req.getRequestURI());
      if (matcher.find()) {
        resp.sendRedirect(
            UriBuilder.fromUri(docsRedirectUrl).path(matcher.group(1)).build().toString());
        return;
      }
    }
    chain.doFilter(request, response);
  }

  @Override
  public void destroy() {}
}
