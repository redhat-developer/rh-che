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

package com.redhat.che.keycloak.token.provider.util;

import static com.google.common.base.Strings.isNullOrEmpty;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.redhat.che.keycloak.token.provider.service.KeycloakTokenProvider;

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;

/**
 * Validates that a user, specified by a keycloak token, matches the user that
 * owns the OpenShift namespace where this class is loaded.
 *
 * @author amisevsk
 */
@Singleton
public final class KeycloakUserValidator {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakUserValidator.class);
    private static final String ACCESS_TOKEN = "access_token";
    private static final int CACHE_TIMEOUT_MINUTES = 10;

    private final KeycloakTokenProvider keycloakTokenProvider;

    /**
     * Cache that stores mappings from keycloak token to OpenShift token. Timeout is
     * equal to CACHE_TIMEOUT_MINUTES.
     *
     * @see TokenLoader
     */
    private final LoadingCache<String, String> keycloakToOpenshiftTokenCache;

    /**
     * Cache that stores mappings from OpenShift tokens to Users. Timeout is equal to
     * CACHE_TIMEOUT_MINUTES.
     *
     * @see UserLoader
     */
    private final LoadingCache<String, String> openShiftTokenToUserCache;

    @Inject
    public KeycloakUserValidator(KeycloakTokenProvider tokenProvider) {
        this.keycloakTokenProvider = tokenProvider;

        this.keycloakToOpenshiftTokenCache = CacheBuilder.newBuilder()
                                                         .maximumSize(100)
                                                         .expireAfterWrite(CACHE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                                                         .build(new TokenLoader());
        this.openShiftTokenToUserCache = CacheBuilder.newBuilder()
                                                     .maximumSize(100)
                                                     .expireAfterWrite(CACHE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                                                     .build(new UserLoader());
    }

    /**
     * Check if user specified by keycloak token matches the user associated with the current
     * Che instance by making request against the relevant endpoints. To improve performance,
     * API responses are cached for a period of time.
     *
     * @param keycloakToken the value of the auth header (i.e. the keycloak token)
     * @return true if the user is authorized to access this instance of Che, false otherwise.
     */
    public boolean matchesUsername(String keycloakToken) {

        if (isNullOrEmpty(keycloakToken)) {
            LOG.info("Received request with null or empty auth value. Returning false");
            return false;
        }
        
        LOG.debug("Keycloak token = {}", keycloakToken);

        String projectOwner = getOpenShiftProjectOwner();
        if ("eclipse".equals(projectOwner)) {
            LOG.info("We are running in a Development Minishift environment => don't check user name.");
            return true;
        } else {
            String openShiftToken = "";
            try {
                openShiftToken = getOpenShiftToken(keycloakToken);
            } catch (ServerException | UnauthorizedException | ForbiddenException | NotFoundException | ConflictException
                    | BadRequestException | IOException e) {
                LOG.error("Could not get OpenShift token due to exception:", e);
            }
            if (isNullOrEmpty(openShiftToken)) {
                return false;
            }
            
            LOG.debug("Openshift token = {}", openShiftToken);
            String openShiftUser = getOpenShiftUser(openShiftToken);
            if (isNullOrEmpty(openShiftUser)) {
                return false;
            }
            LOG.debug("Openshift user = {}", openShiftUser);
            return openShiftUser.equals(getOpenShiftProjectOwner());
        }
    }

    /**
     * Get the OpenShift token associated with a particular Keycloak token.
     * @param keycloakToken the Keycloak token
     * @return the OpenShift token
     * @throws ServerException
     * @throws UnauthorizedException
     * @throws ForbiddenException
     * @throws NotFoundException
     * @throws ConflictException
     * @throws BadRequestException
     * @throws IOException
     *
     * @see TokenLoader
     */
    private String getOpenShiftToken(String keycloakToken) throws ServerException, UnauthorizedException,
            ForbiddenException, NotFoundException, ConflictException, BadRequestException, IOException {
        try {
            // Ensure header has "Bearer:" prefix
            String auth = keycloakToken.startsWith("Bearer") ? keycloakToken : "Bearer " + keycloakToken;
            String openShiftToken = keycloakToOpenshiftTokenCache.get(auth);
            return openShiftToken;
        } catch (ExecutionException e) {
            LOG.error("Exception while getting OpenShift token:", e);
        }
        return null;
    }

    /**
     * Gets the OpenShift user associated with an OpenShift token.
     * @param openShiftToken The OpenShift token
     * @return
     *
     * @see UserLoader
     */
    private String getOpenShiftUser(String openShiftToken) {
        try {
            String currentUsername = openShiftTokenToUserCache.get(openShiftToken);
            if (currentUsername.contains("@")) {
                // Some usernames are email addresses
                currentUsername = currentUsername.split("@")[0];
            }
            return currentUsername;
        } catch (ExecutionException e) {
            LOG.error("Exception while getting user:", e);
        }
        return null;
    }

    /**
     * Get the owner of the current OpenShift namespace. Note that usernames may, in some cases,
     * be email addresses.
     * @return the username, as specified by OpenShift
     */
    private String getOpenShiftProjectOwner() {
        try(OpenShiftClient client = new DefaultOpenShiftClient()) {
            String namespace = client.getNamespace().split("-")[0];
            return namespace;
        }
    }

    /**
     * Load openshift tokens, given keycloak tokens. If an error occurs, loads the
     * empty string.
     *
     * @see CacheLoader
     */
    private class TokenLoader extends CacheLoader<String, String> {

        @Override
        public String load(String keycloakToken) {
            String osoToken = null;
            try {
                osoToken = keycloakTokenProvider.obtainOsoToken(keycloakToken);
            } catch (ServerException | UnauthorizedException | ForbiddenException | NotFoundException
                    | ConflictException | BadRequestException | IOException e) {
                LOG.error("Exception while obtaining OSO token:", e);
            }
            return osoToken != null ? osoToken : "";
        }
    }

    /**
     * Load OpenShift username, given a OpenShift token. In case of error, the empty
     * string is returned.
     *
     * @see CacheLoader
     */
    private class UserLoader extends CacheLoader<String, String> {

        @Override
        public String load(String token) {
            OpenShiftConfig openShiftConfig = new OpenShiftConfigBuilder().withOauthToken(token).build();
            try (OpenShiftClient client = new DefaultOpenShiftClient(openShiftConfig)) {
                String username = client.currentUser().getMetadata().getName();
                return username;
            } catch (KubernetesClientException e) {
                LOG.error("Exception while getting username:", e);
                return "";
            }
        }
    }
}