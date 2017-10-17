/*
 * Copyright (c) 2012-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.che.workspace.logging;

import javax.inject.Singleton;
import org.eclipse.che.plugin.docker.machine.WsAgentLogDirProvider;

/**
 * Provider for the log directory of the wsagent.
 *
 * <p>Returns null, but is expected to return non-null values in bound subclasses
 *
 * @author David Festal
 */
@Singleton
public class Fabric8WsAgentLogDirProvider extends WsAgentLogDirProvider {
  @Override
  public String get() {
    return "/workspace-logs/${CHE_MACHINE_NAME}-ws-agent";
  }
}
