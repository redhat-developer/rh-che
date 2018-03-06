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
package com.redhat.che.multitenant;

import com.google.inject.AbstractModule;
import org.eclipse.che.inject.DynaModule;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftClientFactory;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftEnvironmentProvisioner;
import org.eclipse.che.workspace.infrastructure.openshift.project.OpenShiftProjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DynaModule
public class Fabric8MultiTenantModule extends AbstractModule {

  private static final Logger LOGGER = LoggerFactory.getLogger(Fabric8MultiTenantModule.class);

  @Override
  protected void configure() {
    LOGGER.info("Configuring {}", this.getClass().getName());

    bind(OpenShiftClientFactory.class).to(Fabric8OpenShiftClientFactory.class);
    bind(OpenShiftProjectFactory.class).to(Fabric8OpenShiftProjectFactory.class);
    bind(OpenShiftEnvironmentProvisioner.class).to(RhCheInfraEnvironmentProvisioner.class);
  }
}
