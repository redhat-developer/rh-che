/*
 * Copyright (c) 2016-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.che.plugin.product.info.client;

import com.google.inject.Singleton;
import javax.inject.Inject;
import org.eclipse.che.ide.api.ProductInfoDataProvider;
import org.eclipse.che.ide.api.ProductInfoDataProviderImpl;
import org.vectomatic.dom.svg.ui.SVGResource;

/**
 * Implementation of {@link ProductInfoDataProvider} for OpenShift Che
 *
 * @author Florent Benoit
 */
@Singleton
public class OpenShiftCheProductInfoDataProvider extends ProductInfoDataProviderImpl {

  private final LocalizationConstant locale;
  private final OpenShiftResources resources;

  @Inject
  public OpenShiftCheProductInfoDataProvider(
      LocalizationConstant locale, OpenShiftResources resources) {
    this.locale = locale;
    this.resources = resources;
  }

  @Override
  public String getName() {
    return locale.getProductName();
  }

  @Override
  public String getSupportLink() {
    return locale.getSupportLink();
  }

  @Override
  public String getDocumentTitle() {
    return locale.cheTabTitle();
  }

  @Override
  public String getDocumentTitle(String workspaceName) {
    return locale.cheTabTitle(workspaceName);
  }

  @Override
  public SVGResource getLogo() {
    return resources.logo();
  }

  @Override
  public SVGResource getWaterMarkLogo() {
    return resources.waterMarkLogo();
  }

  @Override
  public String getSupportTitle() {
    return locale.supportTitle();
  }
}
