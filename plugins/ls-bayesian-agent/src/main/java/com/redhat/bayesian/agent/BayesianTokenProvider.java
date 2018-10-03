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

import com.redhat.che.multitenant.WorkspaceSubjectsRegistry;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.multiuser.machine.authentication.server.MachineTokenRegistry;
import org.slf4j.Logger;

/** @author David Festal */
@Path("/bayesian")
@Singleton
public class BayesianTokenProvider {

  private static final Logger LOG = getLogger(BayesianTokenProvider.class);

  private final WorkspaceSubjectsRegistry workspaceSubjectsRegistry;

  @Inject
  public BayesianTokenProvider(
      MachineTokenRegistry machineTokenRegistry,
      WorkspaceSubjectsRegistry workspaceSubjectsRegistry) {
    this.workspaceSubjectsRegistry = workspaceSubjectsRegistry;
  }

  @GET
  @Path("/token")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getBayesianToken() throws NotFoundException {

    String userId = EnvironmentContext.getCurrent().getSubject().getUserId();

    Subject workspaceStarter = workspaceSubjectsRegistry.getSubject(userId);
    if (workspaceStarter == null) {
      LOG.error(
          "Subject that started workspace '{}' was not found during Bayesian recommender token retrieval.",
          userId);
      throw new NotFoundException("Bayesian token not found");
    }
    String keycloakToken = workspaceStarter.getToken();
    if (keycloakToken == null) {
      LOG.error(
          "Keycloak token of the subject that started workspace '{}' (user name = '{}' )was not found during Bayesian recommender token retrieval.",
          userId,
          workspaceStarter.getUserName());
      throw new NotFoundException("Bayesian token not found");
    }
    return Response.ok("\"" + keycloakToken + '"').build();
  }
}
