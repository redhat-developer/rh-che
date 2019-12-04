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
package com.redhat.che.wsmaster.deploy;

import com.google.common.annotations.Beta;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This interceptor must be bound for the {@code doFilter} method of the {@link
 * MultiUserEnvironmentInitializationFilter} and {@link KeycloakEnvironmentInitializationFilter}
 * classes.
 *
 * @author David Festal
 */
@Beta
public class DisableAuthenticationInterceptor implements MethodInterceptor {
  private static final Logger LOG = LoggerFactory.getLogger(DisableAuthenticationInterceptor.class);

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    final Object[] args = invocation.getArguments();

    ServletRequest request = (ServletRequest) args[0];
    ServletResponse response = (ServletResponse) args[1];
    FilterChain filterChain = (FilterChain) args[2];
    if (request instanceof HttpServletRequest) {
      HttpServletRequest httpServletRequest = (HttpServletRequest) request;
      String contextPath = httpServletRequest.getServletPath();
      if (contextPath.startsWith("/fabric8-che-analytics/")
          || contextPath.startsWith("/fabric8-end2end/")) {
        LOG.debug("Disabling authentication on path: {}", contextPath);
        filterChain.doFilter(request, response);
        return null;
      }
    }
    return invocation.proceed();
  }
}
