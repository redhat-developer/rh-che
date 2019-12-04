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
package com.redhat.che.multitenant;

import javax.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftClientConfigFactory;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftClientFactory;
import org.eclipse.che.workspace.infrastructure.openshift.project.OpenShiftProject;
import org.eclipse.che.workspace.infrastructure.openshift.project.OpenShiftProjectFactory;

/** @author Sergii Leshchenko */
public class Fabric8OpenShiftProjectFactory extends OpenShiftProjectFactory {

  private final OpenShiftClientFactory clientFactory;
  private final Fabric8WorkspaceEnvironmentProvider envProvider;

  @Inject
  public Fabric8OpenShiftProjectFactory(
      @Nullable @Named("che.infra.openshift.project") String projectName,
      @Nullable @Named("che.infra.kubernetes.namespace.default") String defaultNamespaceName,
      OpenShiftClientFactory clientFactory,
      Fabric8WorkspaceEnvironmentProvider envProvider,
      UserManager userManager) {
    super(
        projectName,
        null,
        null,
        defaultNamespaceName,
        false,
        clientFactory,
        new OpenShiftClientConfigFactory(),
        userManager);
    this.clientFactory = clientFactory;
    this.envProvider = envProvider;
  }

  @Override
  public OpenShiftProject getOrCreate(RuntimeIdentity identity) throws InfrastructureException {
    Subject currentSubject = EnvironmentContext.getCurrent().getSubject();

    String workspacesNamespace = envProvider.getWorkspacesOpenshiftNamespace(currentSubject);

    return new OpenShiftProject(clientFactory, workspacesNamespace, identity.getWorkspaceId());
  }
}
