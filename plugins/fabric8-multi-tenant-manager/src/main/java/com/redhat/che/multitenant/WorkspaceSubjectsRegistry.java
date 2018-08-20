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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.model.workspace.WorkspaceStatus;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.workspace.shared.dto.event.WorkspaceStatusEvent;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;

/**
 * This class allows maintaining a link between a started workspace and the user who started it.
 * This also allows updating, at any time, the user information (<code>Subject</code>) of users that
 * started workspaces.
 *
 * <p></br>
 *
 * <p>In particular, this allows having access to the up-to-date connection information (userName
 * and Keycloak token) of the user that was used when creating a workspace.
 *
 * <p>This is required for all the use-cases where these user information would be necessary to
 * perform batch-like operations on the user workspaces (such as idling, stop at shutdown, etc ...).
 *
 * <p></br>
 *
 * <p>An example of such use-case is the multi-tenant scenario when deployment is done to OpenShift:
 * the user connection information are required to have access to the OpenShift cluster / namespace
 * where the workspace has been created.
 *
 * @author David Festal
 * @author Sergii Leshchenko
 */
@Singleton
public class WorkspaceSubjectsRegistry implements EventSubscriber<WorkspaceStatusEvent> {

  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Map<String, Subject> workspaceStarters = new HashMap<>();
  private final Multimap<String, String> userIdToWorkspaces = ArrayListMultimap.create();

  @VisibleForTesting
  @Inject
  void subscribe(EventService eventService) {
    eventService.subscribe(this);
  }

  @Override
  public void onEvent(WorkspaceStatusEvent event) {
    String workspaceId = event.getWorkspaceId();
    if (WorkspaceStatus.STOPPED.equals(event.getStatus())) {
      while (userIdToWorkspaces.values().remove(workspaceId)) {}
      workspaceStarters.remove(workspaceId);
    }

    if (WorkspaceStatus.STARTING.equals(event.getStatus())) {
      Subject subject = EnvironmentContext.getCurrent().getSubject();
      if (subject == Subject.ANONYMOUS) {
        throw new IllegalStateException(
            "Workspace "
                + workspaceId
                + " is being started by the 'Anonymous' user.\n"
                + "This shouldn't happen, and workspaces should always be created by a real user.");
      }
      userIdToWorkspaces.put(subject.getUserId(), workspaceId);
      updateSubject(subject);
    }
  }

  /**
   * Returns cached subject for a user with the specified ID.
   *
   * @throws NotFoundException if subject for a user with specified ID not found in cache
   */
  public Subject getSubject(String userId) throws NotFoundException {
    Optional<Subject> subjectOpt =
        workspaceStarters.values().stream().filter(s -> s.getUserId().equals(userId)).findAny();
    if (!subjectOpt.isPresent()) {
      throw new NotFoundException("There is no cached subject with user id '" + userId + '\'');
    }
    return subjectOpt.get();
  }

  /*
   * If some workspaces have been started by the userId contained
   * in this <code>Subject</code>, then the subject (with
   * the userName and token) is updated in the workspace-to-subject
   * cache for all these workspaces.
   */
  private void updateSubject(Subject subject) {
    String token = subject != null ? subject.getToken() : null;
    if (token == null || token.startsWith("machine")) {
      // We are not interested in machine tokens here, but in
      // having the up-to-date token used by the user to connect
      // to the front-end application and create the workspace
      return;
    }
    lock.readLock().lock();
    try {
      String userId = subject.getUserId();
      for (String workspaceId : userIdToWorkspaces.get(userId)) {
        workspaceStarters.put(workspaceId, subject);
      }
    } finally {
      lock.readLock().unlock();
    }
  }
}
