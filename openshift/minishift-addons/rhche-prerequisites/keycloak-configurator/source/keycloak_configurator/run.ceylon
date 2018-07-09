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

import ceylon.interop.java {
	JavaMap,
	JavaStringMap,
	Iter=CeylonIterable,
	javaStringArray
}

import java.lang {
	JavaString=String,
	Types {
		str=nativeString
	},
	ObjectArray
}
import java.nio.file {
	Paths {
		path = get
	},
	Files {
		writeFile = write
	},
	AccessDeniedException
}
import java.util {
	Arrays {
		list=asList
	}
}

import org.keycloak.admin.client {
	KeycloakBuilder {
		keycloakBuilder = builder
	}
}
import org.keycloak.representations.idm {
	IdentityProviderRepresentation,
	AuthenticatorConfigRepresentation
}
import org.jboss.resteasy.client.jaxrs {
	ResteasyClientBuilder
}
import java.util.concurrent {
	TimeUnit {
		 seconds
	}
}

"
 Configure the dedicated Keycloak for running RhChe in standalone mode on Openshift / Minishift
 
 This contains the following changes in the Keycloak server:
 
 - In order to keep the feature that logs in the `oc `client with user OpenShift token at workspace start,
 the minishift account is linked to the user Keycloak account using a Keycloak Openshift identity provider.
 
 - Additionally, in order to avoid the need for the user to explicitly link his minishift account to his main
 Che Keycloak account, we switch the Keycloak autentication to use the Openshift identity provider.
 This way, when the user first logs into the Che server, he logs with his Minishift account and the Minishift
 and Keycloak accounts are linked automatically. Later, when he stars a workspace, and types the `oc project` command
 in a terminal, he's automatically connected to the default `myproject` openshift project available in Minishift by default.
 This provides a behavior very similar with what exists under OSIO.
 
 - Finally, the allowed web origins and redirect urls in Keycloak are modified because by default they contain the
 `che` prefix (OpenShift service name) and here we use `rhche`.
 
 To apply these 3 main changes, this command is run as an OpenShift Job when the Keycloak server is
 applied / started in Minishift by the Minishift addon.
 "
by("David Festal")
suppressWarnings("deprecation")
shared void run() {
	try {
		function argument(String name) => process.namedArgumentValue(name) 
		else process.environmentVariableValue(name.uppercased.replace("-", "_"));
		
		value providerAlias = "openshift-v3";
		value keycloakUrl = argument("keycloak-url") else "http://keycloak:5050/auth";
		value adminPassword = argument("admin-password") else "admin";
		value realmName = argument("realm") else "che";
		value clientId = argument("client-id") else "che-public";
		value providerClientId = argument("provider-client-id") else "kc-client";
		value providerSecret = argument("provider-secret") else "openshift";
		value cheServiceName = argument("che-service-name") else "rhche";
		value providerBaseUrl = argument("provider-base-url");
		
		"You should set the URL of the Minishift console,
		 either with the 'provider-base-url' command line argument,
		 or with the 'PROVIDER_BASE_URL' environment variable."
		assert(exists providerBaseUrl);
		
		value keycloak = keycloakBuilder()
				.serverUrl(keycloakUrl)
				.realm("master")
				.username("admin")
				.password(adminPassword)
				.clientId("admin-cli")
				.resteasyClient(ResteasyClientBuilder().connectionPoolSize(10).establishConnectionTimeout(10, seconds).build())
				.build();
		
		
		print("Connecting to the Keycloak server at `` keycloakUrl ``");
		value systemInfo = keycloak.serverInfo().info.systemInfo;
		print("Connected to the Keycloak server (version `` systemInfo.version ``) at `` keycloakUrl ``");
		
		value realm = keycloak.realm(realmName);
	
		value identityProviders = realm.identityProviders();
		if (! Iter(identityProviders.findAll()).find((idp) => idp.\ialias == providerAlias) exists) {
	
			print("Adding the '`` providerAlias ``' identity provider");
	
			identityProviders.create(object extends IdentityProviderRepresentation() {
				addReadTokenRoleOnCreate = true;
				authenticateByDefault = false;
				\ialias = providerAlias;
				enabled = true;
				linkOnly = false;
				config = entries {
					"baseUrl" -> providerBaseUrl,
					"clientId" -> providerClientId,
					"hideOnLoginPage" -> "false",
					"disableUserInfo" -> "false",
					"clientSecret" -> providerSecret,
					"defaultScope" -> "user:full",
					"useJwksUrl" -> "true"
				};
				linkOnly = false;
				storeToken = true;
				firstBrokerLoginFlowAlias = "first broker login";
				providerId = "openshift-v3";
				updateProfileFirstLoginMode = "on";
				trustEmail = false;
			});
		}
		
		value authFlows = realm.flows();
		
		print("Setting the '`` providerAlias ``' identity provider as the default authentication method");
		
		"The `browser` authentication flow in the Keycloak `` realmName `` realm should have an `identity-provider-redirector` authenticator"
		assert(exists redirector = { 
			for (browserExec in authFlows.getExecutions("browser"))
			if (exists providerId = browserExec.providerId,
				providerId == "identity-provider-redirector")
			browserExec
		}.first);
		
		if (exists redirectorConfigId = redirector.authenticationConfig) {
			value redirectorConfig = authFlows.getAuthenticatorConfig(redirectorConfigId);
			redirectorConfig.config.put(str("defaultProvider"), str(providerAlias));
			authFlows.updateAuthenticatorConfig(redirectorConfigId, redirectorConfig);
		} else {
			authFlows.newExecutionConfig(redirector.id, object extends AuthenticatorConfigRepresentation() {
				\ialias = providerAlias;
				config = entries {
					"defaultProvider" -> providerAlias
				};
			});
			
		}
		
		
		print("Adding the 'read-token' default role for the broker client");
		
		value clients = realm.clients();
	
		"The `broker` client should exist in the ``` realmName ``` Keycloak realm"
		assert (exists broker = Iter(clients.findByClientId("broker")).first);
		
		broker.defaultRoles = array { "read-token" };
		
		clients.get(broker.id).update(broker);	
	
		print("Updating the redirect URL and web origins with 'rhche' instead of 'che'");
			
		"The ``` clientId ``` client should exist in the ``` realm ``` Keycloak realm"
		assert (exists cheClient = Iter(clients.findByClientId(clientId)).first);
		
		cheClient.redirectUris = list (
			for (uri in cheClient.redirectUris) 
			str(uri.replace("://che", "://``cheServiceName``"))
		);
		cheClient.webOrigins = list (
			for (uri in cheClient.webOrigins) 
			str(uri.replace("://che", "://``cheServiceName``"))
		);
		
		clients.get(cheClient.id).update(cheClient);
	} catch(Throwable e) {
		try {
			writeFile(path("/dev/termination-log"), str(e.string).bytes);
		} catch(AccessDeniedException ignored) {}
		
		throw e;
	}
}


JavaMap<JavaString, JavaString> entries({<String->String>*} entries) =>
		JavaMap(JavaStringMap(map(entries)).mapItems((key, item) => str(item)));

ObjectArray<JavaString> array({String*} elems) => javaStringArray(Array(elems));

