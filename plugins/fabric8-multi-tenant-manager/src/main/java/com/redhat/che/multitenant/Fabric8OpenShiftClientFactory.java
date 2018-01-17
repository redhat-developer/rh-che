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

import io.fabric8.kubernetes.client.Config;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftClientFactory;

/** @author Sergii Leshchenko */
@Singleton
public class Fabric8OpenShiftClientFactory extends OpenShiftClientFactory {

  private final Fabric8WorkspaceEnvironmentProvider envProvider;

  @Inject
  public Fabric8OpenShiftClientFactory(Fabric8WorkspaceEnvironmentProvider envProvider) {
    super(null, null, null, null, false);
    this.envProvider = envProvider;
  }

  @Override
  public OpenShiftClient create() throws InfrastructureException {
    Subject currentSubject = EnvironmentContext.getCurrent().getSubject();
    Config config = envProvider.getWorkspacesOpenshiftConfig(currentSubject);
    return new DefaultOpenShiftClient(config);
  }
}
