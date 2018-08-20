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
package com.redhat.che.plugin.analytics.wsagent;

import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.che.workspace.activity.LastAccessTimeFilter;
import org.eclipse.che.workspace.activity.WorkspaceActivityNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Overridden version of the {@link LastAccessTimeFilter} that ignores all accesses to the {@link
 * AnalyticsActivityService}.
 *
 * <p>This is important to avoid a REST messaging endless loop between the wsmaster and the wsagent
 * related to activity notification forwarding.
 *
 * @author David Festal
 */
@Singleton
public class OverridenLastAccessTimeFilter extends LastAccessTimeFilter {

  private static final Logger LOG = LoggerFactory.getLogger(LastAccessTimeFilter.class);
  private static final String PATH_TO_FILTER = "/api" + AnalyticsActivityService.PATH;

  @Inject
  public OverridenLastAccessTimeFilter(WorkspaceActivityNotifier wsActivityEventSender) {
    super(wsActivityEventSender);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    if (request instanceof HttpServletRequest) {
      String path = ((HttpServletRequest) request).getServletPath();
      if (path != null && path.startsWith(PATH_TO_FILTER)) {
        LOG.debug(
            "It's an activity notification from the wsmaster only for analytics: {}. Don't forward this back to the wsmaster !",
            path);
        chain.doFilter(request, response);
        return;
      }
    }

    super.doFilter(request, response, chain);
  }

  @Override
  public void destroy() {}
}
