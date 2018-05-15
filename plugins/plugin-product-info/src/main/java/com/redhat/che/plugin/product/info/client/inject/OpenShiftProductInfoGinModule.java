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
package com.redhat.che.plugin.product.info.client.inject;

import com.google.gwt.inject.client.AbstractGinModule;
import com.redhat.che.plugin.product.info.client.OpenShiftCheProductInfoDataProvider;
import org.eclipse.che.ide.api.ProductInfoDataProviderImpl;
import org.eclipse.che.ide.api.extension.ExtensionGinModule;

/**
 * Module for OpenShift product info
 *
 * @author Florent Benoit
 */
@ExtensionGinModule
public class OpenShiftProductInfoGinModule extends AbstractGinModule {
  /** {@inheritDoc} */
  @Override
  protected void configure() {
    bind(ProductInfoDataProviderImpl.class).to(OpenShiftCheProductInfoDataProvider.class);
  }
}
