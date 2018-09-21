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
package com.redhat.che.multitenant;

import static java.lang.String.format;
import static org.slf4j.LoggerFactory.getLogger;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.provision.env.EnvVarProvider;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.pvc.WorkspaceVolumesStrategy;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.ImagePullSecretProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.InstallerServersPortProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.LogsVolumeMachineProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.PodTerminationGracePeriodProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.ProxySettingsProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.ServiceAccountProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.env.EnvVarsConverter;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.limits.ram.RamLimitProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.restartpolicy.RestartPolicyRewriter;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.server.ServersConverter;
import org.eclipse.che.workspace.infrastructure.openshift.OpenShiftEnvironmentProvisioner;
import org.eclipse.che.workspace.infrastructure.openshift.environment.OpenShiftEnvironment;
import org.eclipse.che.workspace.infrastructure.openshift.provision.OpenShiftUniqueNamesProvisioner;
import org.eclipse.che.workspace.infrastructure.openshift.provision.RouteTlsProvisioner;
import org.slf4j.Logger;

/**
 * Adds env vars needed to perform oc login in workspace containers.
 *
 * <p>TODO replace with {@link EnvVarProvider} when injection of env vars won't hold workspace start
 * request https://github.com/eclipse/che/issues/8989.
 *
 * @author Oleksandr Garagatyi
 */
@Singleton
public class RhCheInfraEnvironmentProvisioner extends OpenShiftEnvironmentProvisioner {
  public static final String TOKEN_VAR = "CHE_OSO_USER_TOKEN";
  public static final String CLUSTER_VAR = "CHE_OSO_CLUSTER";
  public static final String PROJECT_VAR = "CHE_OSO_PROJECT";
  public static final String TRUST_CERTS_VAR = "CHE_OSO_TRUST_CERTS";

  private static final Logger LOG = getLogger(RhCheInfraEnvironmentProvisioner.class);

  private final OpenshiftUserTokenProvider openshiftUserTokenProvider;
  private final TenantDataProvider tenantDataProvider;
  private boolean trustCerts;
  private String wsAgentRoutingTimeout;

  @Inject
  public RhCheInfraEnvironmentProvisioner(
      @Named("che.infra.kubernetes.pvc.enabled") boolean pvcEnabled,
      OpenShiftUniqueNamesProvisioner uniqueNamesProvisioner,
      RouteTlsProvisioner routeTlsProvisioner,
      ServersConverter<OpenShiftEnvironment> openShiftServersConverter,
      EnvVarsConverter envVarsConverter,
      RestartPolicyRewriter restartPolicyRewriter,
      WorkspaceVolumesStrategy volumesStrategy,
      RamLimitProvisioner ramLimitProvisioner,
      InstallerServersPortProvisioner installerServersPortProvisioner,
      LogsVolumeMachineProvisioner logsVolumeMachineProvisioner,
      OpenshiftUserTokenProvider openshiftUserTokenProvider,
      TenantDataProvider tenantDataProvider,
      PodTerminationGracePeriodProvisioner podTerminationGracePeriodProvisioner,
      ImagePullSecretProvisioner imagePullSecretProvisioner,
      ProxySettingsProvisioner proxySettingsProvisioner,
      ServiceAccountProvisioner serviceAccountProvisioner,
      @Named("che.infra.kubernetes.trust_certs") boolean trustCerts,
      @Named("che.fabric8.wsagent_routing_timeout") String wsAgentRoutingTimeout) {
    super(
        pvcEnabled,
        uniqueNamesProvisioner,
        routeTlsProvisioner,
        openShiftServersConverter,
        envVarsConverter,
        restartPolicyRewriter,
        volumesStrategy,
        ramLimitProvisioner,
        installerServersPortProvisioner,
        logsVolumeMachineProvisioner,
        podTerminationGracePeriodProvisioner,
        imagePullSecretProvisioner,
        proxySettingsProvisioner,
        serviceAccountProvisioner);

    this.openshiftUserTokenProvider = openshiftUserTokenProvider;
    this.tenantDataProvider = tenantDataProvider;
    this.trustCerts = trustCerts;
    this.wsAgentRoutingTimeout = wsAgentRoutingTimeout;
  }

  @Override
  public void provision(OpenShiftEnvironment osEnv, RuntimeIdentity identity)
      throws InfrastructureException {
    super.provision(osEnv, identity);

    // here we are at a stage of provisioning when we can add to openshift specific entities only

    Map<String, String> envVars = new HashMap<>();

    try {
      Subject subject = EnvironmentContext.getCurrent().getSubject();
      addTokenEnvVar(envVars, subject);
      addTenantEnvVars(envVars, subject);
      injectEnvVars(osEnv, envVars);
    } catch (InfrastructureException e) {
      // oc login injection is not critical - lets continue start of the workspace if failed
      LOG.error(e.getLocalizedMessage());
    }

    osEnv
        .getRoutes()
        .forEach(
            (name, route) -> {
              Map<String, String> annotations = route.getMetadata().getAnnotations();
              if (annotations.containsKey("org.eclipse.che.server.wsagent/http.path")) {
                annotations.put("haproxy.router.openshift.io/timeout", wsAgentRoutingTimeout);
              }
            });
  }

  private void addTokenEnvVar(Map<String, String> envVars, Subject subject)
      throws InfrastructureException {
    try {
      String osoToken = openshiftUserTokenProvider.getToken(subject);
      if (osoToken == null) {
        throw new InfrastructureException(
            "OSO token not found for user " + getUserDescription(subject));
      } else {
        envVars.put(TOKEN_VAR, osoToken);
      }
    } catch (InfrastructureException e) {
      throw new InfrastructureException(
          format(
              "OSO token retrieval for user '%s' failed with error: %s",
              getUserDescription(subject), e.getMessage()));
    }
  }

  private void addTenantEnvVars(Map<String, String> envVars, Subject subject)
      throws InfrastructureException {
    try {
      UserCheTenantData tenantData = tenantDataProvider.getUserCheTenantData(subject, "user");
      envVars.put(CLUSTER_VAR, tenantData.getClusterUrl());
      envVars.put(PROJECT_VAR, tenantData.getNamespace());
      envVars.put(TRUST_CERTS_VAR, Boolean.toString(trustCerts));
    } catch (InfrastructureException e) {
      throw new InfrastructureException(
          format(
              "OSO tenant data retrieval for user '%s' failed with error: %s",
              getUserDescription(subject), e.getMessage()));
    }
  }

  private void injectEnvVars(OpenShiftEnvironment osEnv, Map<String, String> envVars) {
    Collection<Pod> pods = osEnv.getPods().values();
    pods.forEach(
        pod ->
            pod.getSpec()
                .getContainers()
                .forEach(
                    container -> {
                      envVars.forEach(
                          (key, value) -> {
                            container.getEnv().removeIf(envVar -> envVar.getName().equals(key));
                            container.getEnv().add(new EnvVar(key, value, null));
                          });
                    }));
  }

  private String getUserDescription(Subject subject) {
    return subject.getUserName() + "(" + subject.getUserId() + ")";
  }
}
