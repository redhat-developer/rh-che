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
package com.redhat.che.plugin.analytics.wsmaster;

import com.google.inject.Singleton;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.eclipse.che.api.core.model.workspace.config.SourceStorage;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.server.spi.WorkspaceDao;
import org.eclipse.che.api.workspace.shared.event.WorkspaceCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interceptor that adds some workspace attributes useful for the telemetry at workspace creation
 *
 * @author David Festal
 */
@Singleton
public class AttributesCompleter {
  private static final Logger LOG = LoggerFactory.getLogger(AttributesCompleter.class);

  public static final String SOURCE_TYPE_SAMPLE = "sample";
  public static final String SOURCE_TYPE_BLANK = "blank";

  @Inject
  public AttributesCompleter() {}

  @Inject
  void subscribe(EventService eventService, WorkspaceDao dao) {
    eventService.subscribe(
        new EventSubscriber<WorkspaceCreatedEvent>() {
          @Override
          public void onEvent(WorkspaceCreatedEvent event) {
            try {
              WorkspaceImpl w = (WorkspaceImpl) event.getWorkspace();
              String sourceTypes =
                  w.getConfig()
                      .getProjects()
                      .stream()
                      .map(
                          (p) -> {
                            SourceStorage sourceStorage = p.getSource();
                            if (sourceStorage == null) {
                              return SOURCE_TYPE_BLANK;
                            }
                            String type = sourceStorage.getType();
                            if ("git".equals(type)) {
                              String location = sourceStorage.getLocation();
                              if (location != null
                                  && location.startsWith("https://github.com/che-samples/")) {
                                if (location.equals("https://github.com/che-samples/blank")) {
                                  return SOURCE_TYPE_BLANK;
                                }
                                return SOURCE_TYPE_SAMPLE;
                              }
                            }
                            return type;
                          })
                      .distinct()
                      .map(s -> new StringBuilder().append('\'').append(s).append('\'').toString())
                      .collect(Collectors.joining(", "));

              if (!sourceTypes.isEmpty()) {
                w.getAttributes().put("sourceTypes", sourceTypes);
              }

              w.getAttributes().put("startNumber", "0");

              dao.update(w);
            } catch (Exception e) {
              LOG.error(
                  "Could not update workspace after creation to add the 'sourceTypes' attribute",
                  e);
            }
          }
        });
  }
}
