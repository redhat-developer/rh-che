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

import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.workspace.infrastructure.kubernetes.model.KubernetesRuntimeState;

/**
 * This class is used to provide the user ID and related Openshift namespace that we "guess" from
 * the {@link KubernetesRuntimeState} of a running workspace when the {@link Subject} of the user
 * that started the workspace cannot first be found in the {@link
 * com.redhat.che.multitenant.WorkspaceSubjectsRegistry}.
 *
 * <p>This typically happens during workspace idling after a rolling update.
 *
 * @author David Festal
 */
public class GuessedSubject implements Subject {

  public GuessedSubject(String userId, String namespace) {
    super();
    this.userId = userId;
    this.namespace = namespace;
  }

  private String userId;
  private String namespace;

  @Override
  public String getUserId() {
    return userId;
  }

  @Override
  public String getUserName() {
    return "unknown";
  }

  @Override
  public boolean hasPermission(String domain, String instance, String action) {
    return true;
  }

  @Override
  public void checkPermission(String domain, String instance, String action)
      throws ForbiddenException {}

  @Override
  public String getToken() {
    return null;
  }

  @Override
  public boolean isTemporary() {
    return true;
  }

  public String getNamespace() {
    return namespace;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((namespace == null) ? 0 : namespace.hashCode());
    result = prime * result + ((userId == null) ? 0 : userId.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    GuessedSubject other = (GuessedSubject) obj;
    if (namespace == null) {
      if (other.namespace != null) return false;
    } else if (!namespace.equals(other.namespace)) return false;
    if (userId == null) {
      if (other.userId != null) return false;
    } else if (!userId.equals(other.userId)) return false;
    return true;
  }
}
