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
import org.eclipse.che.plugin.docker.machine.ExecAgentLogDirProvider;

/**
 * Provider for the log directory of the exec agent.
 *
 * @author David Festal
 */
@Singleton
public class Fabric8ExecAgentLogDirProvider extends ExecAgentLogDirProvider {
  @Override
  public String get() {
    return "/workspace-logs/${CHE_MACHINE_NAME}-exec-agent";
  }
}
