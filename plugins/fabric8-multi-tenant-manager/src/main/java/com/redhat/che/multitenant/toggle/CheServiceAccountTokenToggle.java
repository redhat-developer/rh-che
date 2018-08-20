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
package com.redhat.che.multitenant.toggle;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import no.finn.unleash.DefaultUnleash;
import no.finn.unleash.Unleash;
import no.finn.unleash.UnleashContext;
import no.finn.unleash.util.UnleashConfig;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Toggle for canary rollout of moving Che to Service Account Token
 * @see <a href="https://github.com/redhat-developer/rh-che/issues/532">Change the token used to access Che tenant namespace in OSIO</a>
 */
@Singleton
public class CheServiceAccountTokenToggle {
  private static final Logger LOG = LoggerFactory.getLogger(CheServiceAccountTokenToggle.class);
  private static final String APP_NAME = "rh-che";
  private static final String FEATURE_NAME = "che.serviceaccount.lockdown";
  private static final String UNLEASH_API_ENDPOINT = "http://f8toggles/api";
  private static final String DEFAULT_HOSTNAME = "che-host";
  private static final String HOSTNAME_ENV_VAR = "HOSTNAME";
  private Unleash unleash;

  @PostConstruct
  private void initUnleash() {
    UnleashConfig config =
        UnleashConfig.builder()
            .appName(APP_NAME)
            .instanceId(getHostname())
            .unleashAPI(UNLEASH_API_ENDPOINT)
            .build();
    this.unleash = new DefaultUnleash(config);
  }

  public boolean useCheServiceAccountToken(final String userId) {
    UnleashContext context = getContext(userId);
    return unleash.isEnabled(FEATURE_NAME, context);
  }

  private UnleashContext getContext(final String userId) {
    return UnleashContext.builder().userId(userId).build();
  }

  private String getHostname() {
    String hostname = System.getenv(HOSTNAME_ENV_VAR);
    LOG.info("HOSTNAME: {}", hostname);
    return StringUtils.isBlank(hostname) ? DEFAULT_HOSTNAME : hostname;
  }
}
