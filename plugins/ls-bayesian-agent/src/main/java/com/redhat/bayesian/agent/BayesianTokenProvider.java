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
package com.redhat.bayesian.agent;

import static org.slf4j.LoggerFactory.getLogger;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.workspace.server.WorkspaceSubjectRegistry;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.multiuser.machine.authentication.server.MachineTokenRegistry;
import org.slf4j.Logger;

@Path("/bayesian")
@Singleton
public class BayesianTokenProvider {
  private static final Logger LOG = getLogger(BayesianTokenProvider.class);

  @Inject private MachineTokenRegistry machineTokenRegistry;
  @Inject private WorkspaceSubjectRegistry workspaceSubjectRegistry;

  @GET
  @Path("/token")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getBayesianToken(@HeaderParam(HttpHeaders.AUTHORIZATION) String machineToken)
      throws NotFoundException {
    String workspaceId = machineTokenRegistry.getWorkspaceId(machineToken);
    LOG.debug("workspaceId for Bayesian recommender token retrieval: {}", workspaceId);
    Subject workspaceStarter = workspaceSubjectRegistry.getWorkspaceStarter(workspaceId);
    if (workspaceStarter == null) {
      LOG.error(
          "Subject that started workspace '{}' was not found during Bayesian recommender token retrieval.",
          workspaceId);
      throw new NotFoundException("Bayesian token not found");
    }
    String keycloakToken = workspaceStarter.getToken();
    if (keycloakToken == null) {
      LOG.error(
          "Keycloak token of the subject that started workspace '{}' (user name = '{}' )was not found during Bayesian recommender token retrieval.",
          workspaceId,
          workspaceStarter.getUserName());
      throw new NotFoundException("Bayesian token not found");
    }
    return Response.ok("\"" + keycloakToken + '"').build();
  }
}
