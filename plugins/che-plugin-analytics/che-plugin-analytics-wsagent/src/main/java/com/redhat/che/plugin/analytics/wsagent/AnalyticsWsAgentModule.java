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

import com.google.inject.AbstractModule;
import org.eclipse.che.inject.DynaModule;
import org.eclipse.che.workspace.activity.LastAccessTimeFilter;

/**
 * Module that allows pushing workspace events to the Segment Analytics tracking tool
 *
 * @author David festal
 */
@DynaModule
public class AnalyticsWsAgentModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(LastAccessTimeFilter.class).to(OverridenLastAccessTimeFilter.class).asEagerSingleton();
    bind(AnalyticsActivityService.class);
  }
}
