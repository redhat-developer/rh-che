package com.redhat.che.keycloak.server;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.ForbiddenException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.UnauthorizedException;
import org.eclipse.che.api.core.rest.DefaultHttpJsonRequest;
import org.eclipse.che.api.core.rest.HttpJsonRequest;
import org.eclipse.che.api.core.rest.HttpJsonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Injects DNS resolvers and ensures that it is neither empty array nor single value array with null or empty string.
 *
 * @author Alexander Garagatyi
 */
@Singleton
public class KeycloakPropertiesProvider implements Provider<Boolean> {
    private String keycloakSettingsEnpoint;
    private Boolean keycloakDisabled = Boolean.FALSE;
    private boolean keycloakDisabledRetrieved = false;
    private static final Logger LOG = LoggerFactory.getLogger(KeycloakPropertiesProvider.class);

    class LocalHttpJsonRequest extends DefaultHttpJsonRequest {
        public LocalHttpJsonRequest(String url) {
            super(url);
        }
    }

    @Inject
    public KeycloakPropertiesProvider(@Named("che.api") String apiEndpoint) {
        keycloakSettingsEnpoint = apiEndpoint.concat("/keycloak/settings");
        LOG.info("Endpoint = " + keycloakSettingsEnpoint);
        retrieveKeycloakDisabled();
    }

    private void retrieveKeycloakDisabled() {
        if (! keycloakDisabledRetrieved) {
            LOG.info("Submitting request...");
            HttpJsonRequest req = new LocalHttpJsonRequest(keycloakSettingsEnpoint);
            try {
                HttpJsonResponse resp = req.request();
                String respAsString = resp.asString();
                LOG.info("  => Response = " + respAsString);
                String disabledSetting = req.request().asProperties().get("che.keycloak.disabled");
                LOG.info("    => Disabled = " + disabledSetting);
                keycloakDisabled = "true".equals(disabledSetting);
                LOG.info("Pproperty 'che.keycloak.disabled' ==> " + keycloakDisabled);
                keycloakDisabledRetrieved = true;
            } catch (ServerException 
                | UnauthorizedException | ForbiddenException
                | NotFoundException | ConflictException
                | BadRequestException | IOException e) {
                LOG.error("Error while retrieving the Keycloak enablement property", e);
            }
        }
    }

    @Override
    public Boolean get() {
        retrieveKeycloakDisabled();
        return keycloakDisabled;
    }
}
