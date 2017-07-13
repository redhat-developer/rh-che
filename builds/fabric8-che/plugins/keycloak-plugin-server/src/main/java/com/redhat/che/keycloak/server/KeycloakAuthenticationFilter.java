/*******************************************************************************
 * Copyright (c) 2017 Red Hat inc.

 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat - Initial Contribution
 *******************************************************************************/
package com.redhat.che.keycloak.server;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.che.keycloak.server.oso.service.account.ServiceAccountInfoProvider;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;

public class KeycloakAuthenticationFilter extends org.keycloak.adapters.servlet.KeycloakOIDCFilter {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakAuthenticationFilter.class);

    private boolean keycloakDisabled;

    @Inject
    private KeycloakUserChecker userChecker;

    @Inject
    private ServiceAccountInfoProvider serviceAccountInfoProvider;

    @Inject
    public KeycloakAuthenticationFilter(@Named("che.keycloak.disabled") boolean keycloakDisabled) {
        this.keycloakDisabled = keycloakDisabled;
        if (keycloakDisabled) {
            LOG.info("Keycloak is disabled");
        }
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (keycloakDisabled) {
            chain.doFilter(req, res);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) req;
        String authHeader = request.getHeader("Authorization");
        String requestURI = request.getRequestURI();
        String requestScheme = req.getScheme();

        if (authHeader == null) {
            LOG.debug("No 'Authorization' header for {}", requestURI);
        }

        if (isSystemStateRequest(requestURI) || isWebsocketRequest(requestURI, requestScheme)
                || isKeycloakSettingsRequest(requestURI) || isWorkspaceAgentRequest(authHeader)) {
            LOG.debug("Skipping {}", requestURI);
            chain.doFilter(req, res);
        } else if (userChecker.matchesUsername(authHeader)) {
            super.doFilter(req, res, chain);
            LOG.debug("{} status : {}", request.getRequestURL(), ((HttpServletResponse) res).getStatus());
        } else {
            HttpServletResponse response = (HttpServletResponse) res;
            response.sendError(403);
            return;
        }
    }

    /**
     * @param requestURI
     * @return true if request is made against system state endpoint which is
     *         used in OpenShift liveness & readiness probes, false otherwise
     */
    private boolean isSystemStateRequest(String requestURI) {
        return requestURI.endsWith("/api/system/state");
    }

    /**
     * @param requestURI
     * @return true if request is made against endpoint which provides keycloak
     *         status (enabled / disabled), false otherwise
     */
    private boolean isKeycloakSettingsRequest(String requestURI) {
        return requestURI.endsWith("/keycloak/settings");
    }

    /**
     * @param authHeader
     * @return true if "Authorization" header has valid openshift token that is
     *         used for communication between wsagent and wsmaster, false
     *         otherwise
     */
    private boolean isWorkspaceAgentRequest(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Wsagent")) {
            String token = authHeader.replaceFirst("Wsagent ", "");
            return isValidToken(token);
        }
        return false;
    }

    private boolean isWebsocketRequest(String requestURI, String requestScheme) {
        return requestURI.endsWith("/ws") || requestURI.endsWith("/eventbus") || requestScheme.equals("ws")
                || requestScheme.equals("wss") || requestURI.contains("/websocket/")
                || requestURI.endsWith("/token/user");
    }

    /**
     * OpenShift default service account token is used in "Authorization" header
     * for communication between wsagent and wsmaster. The method checks if the
     * token is valid by fetching namespace info and comparing it with expected 
     * service account namespace
     * 
     * @param token
     * @return true if openshift token is valid and matches service account namespace, false otherwise
     */
    private boolean isValidToken(final String token) {
        LOG.debug("Validating workspace agent token");
        Config config = new ConfigBuilder().withOauthToken(token).build();
        try (OpenShiftClient client = new DefaultOpenShiftClient(config)) {
            String namespace = client.getConfiguration().getNamespace();
            LOG.debug("Validating the token against namespace '{}'", namespace);
            return serviceAccountInfoProvider.getNamespace().equals(namespace);
        } catch (Exception e) {
            LOG.debug("The token is not valid {}", token);
            return false;
        }
    }

}
