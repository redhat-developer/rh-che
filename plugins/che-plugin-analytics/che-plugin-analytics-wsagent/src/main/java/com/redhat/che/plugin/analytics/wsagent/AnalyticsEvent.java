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

import static com.redhat.che.plugin.analytics.wsagent.EventProperties.*;

public enum AnalyticsEvent {
  WORKSPACE_STARTED("Start Workspace in Che", 3),
  WORKSPACE_OPENED("Refresh Workspace in Che", 3),
  WORKSPACE_USED("Use Workspace in Che"),
  WORKSPACE_INACTIVE("Keep Workspace Inactive in Che"),
  WORKSPACE_STOPPED("Stop Workspace in Che"),
  EDITOR_USED("Edit Workspace File in Che", 30, new String[] {PROGRAMMING_LANGUAGE}),
  PUSH_TO_REMOTE("Push to remote in Che", 10),
  COMMIT_LOCALLY("Commit locally in Che", 10);

  private final String name;
  private final int expectedDuration;
  private final String[] propertiesToCheck;

  AnalyticsEvent(String name, int expectedDurationSeconds, String[] propertiesToCheck) {
    this.name = name;
    this.expectedDuration = expectedDurationSeconds;
    this.propertiesToCheck = propertiesToCheck;
  }

  AnalyticsEvent(String name, int expectedDurationSeconds) {
    this(name, expectedDurationSeconds, new String[0]);
  }

  AnalyticsEvent(String name) {
    this(name, -1, new String[0]);
  }

  @Override
  public String toString() {
    return name;
  }

  public int getExpectedDurationSeconds() {
    return expectedDuration;
  }

  public String[] getPropertiesToCheck() {
    return propertiesToCheck;
  }
}
