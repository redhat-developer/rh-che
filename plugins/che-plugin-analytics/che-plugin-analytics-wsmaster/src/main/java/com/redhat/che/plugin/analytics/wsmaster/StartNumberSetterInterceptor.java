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

import com.google.common.annotations.Beta;
import javax.inject.Inject;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.eclipse.che.api.core.model.workspace.Workspace;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.server.spi.WorkspaceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This interceptor must be bound for the method {@link WorkspaceRuntimes#startAsync(Workspace,
 * String, Map<String, String>)}
 *
 * @author David Festal
 */
@Beta
public class StartNumberSetterInterceptor implements MethodInterceptor {
  private static final Logger LOG = LoggerFactory.getLogger(StartNumberSetterInterceptor.class);

  @Inject WorkspaceDao dao;

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    final Object[] args = invocation.getArguments();

    try {
      final Workspace w = (Workspace) args[0];
      String startNumber = w.getAttributes().get("startNumber");
      if (startNumber != null) {
        String newStartNumber = Long.toString(Long.parseLong(startNumber) + 1);
        w.getAttributes().put("startNumber", newStartNumber);
        dao.update((WorkspaceImpl) w);
      }
    } catch (Exception e) {
      LOG.error("Could not update the workspace 'startNumber' attribute after start", e);
    }

    return invocation.proceed();
  }
}
