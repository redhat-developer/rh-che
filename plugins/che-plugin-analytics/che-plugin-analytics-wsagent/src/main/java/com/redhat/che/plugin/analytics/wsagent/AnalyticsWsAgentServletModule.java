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
