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
package com.redhat.che.multitenant.multicluster;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MultiClusterOpenShiftProxy {
  private static final Logger LOG = LoggerFactory.getLogger(MultiClusterOpenShiftProxy.class);

  private String url;

  @Inject
  public MultiClusterOpenShiftProxy(
      @Named("che.fabric8.multicluster.oso.proxy.url")
          String fabric8MultiClusterOpenShiftProxyUrl) {
    LOG.info("fabric8MultiClusterOpenShiftProxyUrl = {}", fabric8MultiClusterOpenShiftProxyUrl);
    this.url = fabric8MultiClusterOpenShiftProxyUrl;
  }

  public String getUrl() {
    return this.url;
  }

  public String getUrlWithIdentityIdQueryParameter(String identityId) {
    return this.url + "?identity_id=" + identityId;
  }
}
