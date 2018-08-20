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

import com.google.inject.servlet.ServletModule;
import org.eclipse.che.inject.DynaModule;

/**
 * Module that allows pushing workspace events to the Segment and Woopra Analytics tracking tools
 *
 * @author David Festal
 */
@DynaModule
public class AnalyticsWsAgentServletModule extends ServletModule {

  @Override
  protected void configureServlets() {
    filter("/api/*").through(UrlToEventFilter.class);
  }
}
