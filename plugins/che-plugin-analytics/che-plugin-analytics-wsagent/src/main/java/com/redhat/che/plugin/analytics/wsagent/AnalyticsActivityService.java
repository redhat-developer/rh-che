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
package com.redhat.che.plugin.analytics.wsagent;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import org.eclipse.che.api.core.rest.Service;

/**
 * This service endpoint receives from the Wsmaster workspaces-related events that are relevant for
 * the AnalyticsManager (telemetry).
 *
 * <p>Such avents are activity notification or workspace stop event.
 *
 * <p>The endpoints only answers OK, because the corresponding API calls are already detected and
 * leveraged by the {@link UrlToEventFilter} filter.
 *
 * @author David Festal
 */
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
