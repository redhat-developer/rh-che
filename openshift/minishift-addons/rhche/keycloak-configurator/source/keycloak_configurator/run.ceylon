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
	}
}
import java.util {
	Arrays {
		list=asList
	}
}

import org.keycloak.admin.client {
	Keycloak {
		getKeycloak=getInstance
	}
}
import org.keycloak.representations.idm {
	IdentityProviderRepresentation,
	AuthenticatorConfigRepresentation
}

"Configure the embedded Keycloak for running RhChe on Minishift"
suppressWarnings("deprecation")
shared void run() {
	try {
		value keycloakUrl = process.namedArgumentValue("keycloak-url") else "http://keycloak:5050/auth";
		value adminPassword = process.namedArgumentValue("admin-password") else "admin";
		value realmName = process.namedArgumentValue("realm") else "che";
		value clientId = process.namedArgumentValue("client-id") else "che-public";
		value providerClientId = process.namedArgumentValue("provider-client-id") else "kc-client";
		value providerSecret = process.namedArgumentValue("provider-secret") else "openshift";
		value cheServiceName = process.namedArgumentValue("che-service-name") else "rhche";
		value providerAlias = "openshift-v3";
		
		value minishiftUrl = process.namedArgumentValue("minishift-url")
		else process.environmentVariableValue("MINISHIFT_CONSOLE");
		"You should set the URL of the Minishift console,
		 either with the 'minishift-url' command line argument,
		 or with the 'MINISHIFT_CONSOLE' environment variable."
		assert(exists minishiftUrl);
		
		value keycloak = getKeycloak(
			keycloakUrl,
			"master",
			"admin",
			adminPassword,
			"admin-cli");
		
		
		value systemInfo = keycloak.serverInfo().info.systemInfo;
		print("Connected to the Keycloak server (version `` systemInfo.version ``) as `` systemInfo.userName `` at `` keycloakUrl ``");
		
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
					"baseUrl" -> minishiftUrl,
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
				this.config = entries {
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
		writeFile(path("/dev/termination-log"), str(e.string).bytes);
		throw e;
	}
}


JavaMap<JavaString, JavaString> entries({<String->String>*} entries) =>
		JavaMap(JavaStringMap(map(entries)).mapItems((key, item) => str(item)));

ObjectArray<JavaString> array({String*} elems) => javaStringArray(Array(elems));

