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

public interface EventProperties {
  public static final String PROGRAMMING_LANGUAGE = "programming language";
  public static final String WORKSPACE_ID = "workspace id";
  public static final String WORKSPACE_NAME = "workspace name";
  public static final String STACK_ID = "stack id";
  public static final String FACTORY_ID = "factory id";
  public static final String FACTORY_NAME = "factory name";
  public static final String FACTORY_OWNER = "factory owner";
  public static final String FACTORY_URL = "factory url";
  public static final String CREATED = "creation time";
  public static final String UPDATED = "start time";
  public static final String STOPPED = "last stop time";
  public static final String AGE = "age";
  public static final String RETURN_DELAY = "return delay ";
  public static final String FIRST_START = "first start";
  public static final String LAST_WORKSPACE_FAILED = "last workspace failed";
  public static final String LAST_WORKSPACE_FAILURE = "last workspace failure";
  public static final String OSIO_SPACE_ID = "osio space id";
  public static final String SOURCE_TYPES = "source types";
  public static final String START_NUMBER = "start number";
}
