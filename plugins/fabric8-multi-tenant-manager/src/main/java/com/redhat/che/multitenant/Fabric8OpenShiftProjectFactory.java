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

import javax.inject.Inject;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftClientFactory;
import org.eclipse.che.workspace.infrastructure.openshift.project.OpenShiftProject;
import org.eclipse.che.workspace.infrastructure.openshift.project.OpenShiftProjectFactory;

/** @author Sergii Leshchenko */
public class Fabric8OpenShiftProjectFactory extends OpenShiftProjectFactory {

  private final OpenShiftClientFactory clientFactory;
  private final Fabric8WorkspaceEnvironmentProvider envProvider;

  @Inject
  public Fabric8OpenShiftProjectFactory(
      OpenShiftClientFactory clientFactory, Fabric8WorkspaceEnvironmentProvider envProvider) {
    super(null, clientFactory);
    this.clientFactory = clientFactory;
    this.envProvider = envProvider;
  }

  @Override
  public OpenShiftProject create(String workspaceId) throws InfrastructureException {
    Subject currentSubject = EnvironmentContext.getCurrent().getSubject();

    String workspacesNamespace = envProvider.getWorkspacesOpenshiftNamespace(currentSubject);

    return new OpenShiftProject(clientFactory, workspacesNamespace, workspaceId);
  }
}
