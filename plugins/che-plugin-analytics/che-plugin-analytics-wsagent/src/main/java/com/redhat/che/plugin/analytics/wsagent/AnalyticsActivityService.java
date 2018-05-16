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
package com.redhat.che.plugin.analytics.wsagent;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import org.eclipse.che.api.core.rest.Service;

@Singleton
@Path(AnalyticsActivityService.PATH)
public class AnalyticsActivityService extends Service {
  public static final String PATH = "/fabric8-che-analytics";

  private final AnalyticsManager manager;

  @Inject
  public AnalyticsActivityService(AnalyticsManager manager) {
    this.manager = manager;
  }

  @GET
  @Path("activity")
  public Response onActivity() {
    return Response.ok().build();
  }

  @GET
  @Path("stopped")
  public Response stopped() {
    manager.destroy();
    return Response.ok().build();
  }
}
