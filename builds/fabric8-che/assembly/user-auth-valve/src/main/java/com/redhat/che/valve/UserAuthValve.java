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

package com.redhat.che.valve;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.adapters.tomcat.KeycloakAuthenticatorValve;

/**
 * Performs Keycloak and OpenShift.io user validation. Prompts user to login if necessary
 * and checks if the logged in user has access to the current Che instance.
 *
 * @see KeycloakAuthenticatorValve
 *
 * @author amisevsk
 */
public class UserAuthValve extends KeycloakAuthenticatorValve {

    private static final Log LOG = LogFactory.getLog(UserAuthValve.class);
    private static final String USER_VALIDATOR_ENDPOINT = "http://che-host:8080/api/token/user";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String KEYCLOAK_SETTINGS_ENDPOINT = "http://che-host:8080/api/keycloak/settings";
    private static final Pattern DISABLED_SETTING_PATTERN = Pattern.compile(".*\"che\\.keycloak\\.disabled\":\"([^ \"]+)\".*");
    
    private boolean keycloakDisabledRetrieved = false;
    private boolean keycloakDisabled = false;
    
    @Override
    public boolean authenticate(Request request, HttpServletResponse response) throws IOException {
        if (isKeycloakDisabled()) {
            LOG.info("Keycloak is disabled => Bypassing authentification");
            GenericPrincipal genericPrincipal = new GenericPrincipal("developer", "developer", Arrays.asList("developer"));
            request.setUserPrincipal(genericPrincipal);
            request.setAuthType(HttpServletRequest.BASIC_AUTH);
            return true;
        }
        if (super.authenticate(request, response)) {
            String auth = getToken(request);
            if (auth != null && userMatches(auth)) {
                return true;
            }
            response.sendError(403);
        }
        return false;
    }

    /**
     * Verify that a logged in user has access to the current project by making a request
     * against the /api/token/user endpoing on Che server.
     *
     * <p> Base URL for the request is set to the default service name for Eclipse Che ({@code che-host})
     * @param keycloakToken value of the auth header on the request (i.e. the keycloak token).
     * @return true if the user matches the current project owner, false otherwise
     */
    private boolean userMatches(String keycloakToken) {
        URL url;
        HttpURLConnection conn;
        try {
            url = new URL(USER_VALIDATOR_ENDPOINT);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.addRequestProperty(AUTHORIZATION_HEADER, keycloakToken);
            try(InputStream is = conn.getInputStream()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                return "true".equals(response.toString());
            }
        } catch (IOException e) {
            LOG.fatal("Exception while validating user", e);
        }
        return false;
    }

    /**
     * Gets keycloak token from request.
     * @param request
     * @return
     */
    private String getToken(Request request) {
        KeycloakSecurityContext ksc =
                (KeycloakSecurityContext)request.getAttribute(KeycloakSecurityContext.class.getName());
        return ksc.getTokenString();
    }

    public boolean isKeycloakDisabled() {
        retrieveKeycloakDisabledSetting();
        return keycloakDisabled;
    }
    
    public void retrieveKeycloakDisabledSetting() {
        if (! keycloakDisabledRetrieved) {
            URL url;
            HttpURLConnection conn;
            try {
                url = new URL(KEYCLOAK_SETTINGS_ENDPOINT);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                if (LOG.isInfoEnabled()) {
                    LOG.info("KeycloakSettings = " + response);
                }
                Matcher matcher = DISABLED_SETTING_PATTERN.matcher(response.toString());
                if (matcher.matches()) {
                    String value = matcher.group(1);
                    keycloakDisabled = "true".equals(value);
                    keycloakDisabledRetrieved = true;
                }
            } catch (IOException e) {
                LOG.error("Exception during Keycloak settings retrieval", e);
            }
        }
    }
}
