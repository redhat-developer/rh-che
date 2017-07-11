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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

@Singleton
public class KeycloakUserChecker {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakUserChecker.class);
    private static final String ENDPOINT = "http://che-host:8080/api/token/user";

    @Inject
    private KeycloakHttpJsonRequestFactory requestFactory;
    
    /**
     * Cache that stores mappings from user to authorization status.
     */
    private LoadingCache<String, Boolean> cache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<String, Boolean>() {
                @Override
                public Boolean load(String auth) throws Exception {
                    try {
                        String response = requestFactory.fromUrl(ENDPOINT)
                                                                              .useGetMethod()
                                                                              .setAuthorizationHeader(auth)
                                                                              .request().asString();
                        return "true".equals(response);
                    } catch (ServerException | UnauthorizedException | ForbiddenException | NotFoundException
                            | ConflictException | BadRequestException | IOException e) {
                        LOG.error("Exception while calling the user auth endpoint:", e);
                    }
                    return false;
                }
            });

    /**
     * Check if a provided keycloak token matches the OpenShift user that owns the current
     * namespace.
     * @param auth the keycloak token
     * @return true if keycloak token is assigned to owner of namespace, false otherwise.
     */
    public boolean matchesUsername(String auth) {
        if (auth == null) {
            return false;
        }
        try {
            Boolean userMatches = cache.get(auth);
            if (userMatches != null) {
                return userMatches;
            }
        } catch (ExecutionException e) {
            LOG.error("Exception while checking user auth:", e);
        }
        return false;
    }

}
