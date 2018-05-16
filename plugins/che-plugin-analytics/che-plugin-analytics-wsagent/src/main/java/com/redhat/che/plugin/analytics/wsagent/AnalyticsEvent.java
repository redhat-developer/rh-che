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

import static com.redhat.che.plugin.analytics.wsagent.EventProperties.*;

public enum AnalyticsEvent {
  WORKSPACE_STARTED("Start Workspace in Che", 3),
  WORKSPACE_OPENED("Refreshed Workspace in Che", 3),
  WORKSPACE_USED("Use Workspace in Che"),
  WORKSPACE_INACTIVE("Keep Workspace Inactive in Che"),
  WORKSPACE_STOPPED("Stop Workspace in Che"),
  EDITOR_USED("Edit Workspace File In Che", 30, new String[] {PROGRAMMING_LANGUAGE});

  private String name;
  private int expectedDuration;
  private String[] propertiesToCheck;

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
