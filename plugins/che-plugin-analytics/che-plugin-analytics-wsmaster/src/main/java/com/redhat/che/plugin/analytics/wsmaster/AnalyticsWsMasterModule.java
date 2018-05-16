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
package com.redhat.che.plugin.analytics.wsmaster;

import com.google.inject.AbstractModule;
import org.eclipse.che.inject.DynaModule;

/**
 * Module that allows pushing workspace events to the Segment Analytics tracking tool
 *
 * @author David festal
 */
@DynaModule
public class AnalyticsWsMasterModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(AnalyticsSettingsService.class);
  }
}
