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

import com.google.inject.servlet.ServletModule;
import org.eclipse.che.inject.DynaModule;

/**
 * Module that allows pushing workspace events to the Segment Analytics tracking tool
 *
 * @author David festal
 */
@DynaModule
public class AnalyticsWsMasterServletModule extends ServletModule {

  @Override
  protected void configureServlets() {
    filter("/activity/*").through(ForwardActivityFilter.class);
  }
}
