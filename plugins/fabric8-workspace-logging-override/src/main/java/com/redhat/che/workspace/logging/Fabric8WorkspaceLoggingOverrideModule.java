package com.redhat.che.workspace.logging;
/*
 * Copyright (c) 2016-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */

import com.google.inject.AbstractModule;
import org.eclipse.che.inject.DynaModule;
import org.eclipse.che.plugin.docker.machine.ExecAgentLogDirProvider;
import org.eclipse.che.plugin.docker.machine.WsAgentLogDirProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DynaModule
public class Fabric8WorkspaceLoggingOverrideModule extends AbstractModule {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(Fabric8WorkspaceLoggingOverrideModule.class);

  @Override
  protected void configure() {
    LOGGER.info("Configuring {}", this.getClass().getName());

    bind(ExecAgentLogDirProvider.class).to(Fabric8ExecAgentLogDirProvider.class);
    bind(WsAgentLogDirProvider.class).to(Fabric8WsAgentLogDirProvider.class);
  }
}
