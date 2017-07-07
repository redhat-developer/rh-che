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

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeycloakAuthenticationFilter extends org.keycloak.adapters.servlet.KeycloakOIDCFilter {
    
    private static final Logger LOG = LoggerFactory.getLogger(KeycloakAuthenticationFilter.class);

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        String authHeader = request.getHeader("Authorization");
        String requestURI = request.getRequestURI();
        String requestScheme = req.getScheme();

        if (authHeader == null) {
            LOG.debug("No 'Authorization' header for {}", requestURI);
        }

        if (isSystemStateRequest(requestURI) || isWebsocketRequest(requestURI, requestScheme)
                || isInternalRequest(authHeader)) {
            LOG.debug("Skipping {}", requestURI);
            chain.doFilter(req, res);
        } else if (KeycloakUserChecker.matchesUsername(authHeader)) {
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

    private boolean isInternalRequest(String authHeader) {
        return "Internal".equals(authHeader);
    }

    private boolean isWebsocketRequest(String requestURI, String requestScheme) {
        return requestURI.endsWith("/ws") || requestURI.endsWith("/eventbus") || requestScheme.equals("ws")
                || requestScheme.equals("wss") || requestURI.contains("/websocket/")
                || requestURI.endsWith("/token/user");
    }
}
