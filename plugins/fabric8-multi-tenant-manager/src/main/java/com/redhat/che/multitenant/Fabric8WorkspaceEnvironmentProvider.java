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
package com.redhat.che.multitenant;

import com.google.gson.JsonParser;
import com.redhat.che.multitenant.multicluster.MultiClusterOpenShiftProxy;
import com.redhat.che.multitenant.toggle.CheServiceAccountTokenToggle;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.inject.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Fabric8WorkspaceEnvironmentProvider {

  private static final Logger LOG =
      LoggerFactory.getLogger(Fabric8WorkspaceEnvironmentProvider.class);

  private final MultiClusterOpenShiftProxy multiClusterOpenShiftProxy;
  private final CheServiceAccountTokenToggle cheServiceAccountTokenToggle;
  private final TenantDataProvider tenantDataProvider;

  private String cheServiceAccountToken;

  @Inject
  public Fabric8WorkspaceEnvironmentProvider(
      @Named("che.fabric8.multitenant") boolean fabric8CheMultitenant,
      MultiClusterOpenShiftProxy multiClusterOpenShiftProxy,
      CheServiceAccountTokenToggle cheServiceAccountTokenToggle,
      TenantDataProvider tenantDataProvider) {
    if (!fabric8CheMultitenant) {
      throw new ConfigurationException(
          "Fabric8 Che Multitetant is disabled. "
              + "che.infra.openshift.project must be configured with non null value");
    }
    this.multiClusterOpenShiftProxy = multiClusterOpenShiftProxy;
    this.cheServiceAccountTokenToggle = cheServiceAccountTokenToggle;
    this.tenantDataProvider = tenantDataProvider;
  }

  @Inject
  private void setServiceAccountToken(
      @Nullable @Named("che.openshift.service_account.id") String serviceAccId,
      @Nullable @Named("che.openshift.service_account.secret") String serviceAccSecret,
      @Named("che.fabric8.auth.endpoint") String authEndpoint) {

    if (serviceAccId == null || serviceAccId.isEmpty()) {
      return;
    }
    OkHttpClient client = new OkHttpClient();
    RequestBody requestBody =
        new FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", serviceAccId)
            .add("client_secret", serviceAccSecret)
            .build();

    Request request =
        new Request.Builder().url(authEndpoint + "/api/token").post(requestBody).build();
    try (Response response = client.newCall(request).execute()) {
      // Ignore IDE warning:
      // body is not null after call of execute() according to javadocs of method body()
      cheServiceAccountToken =
          new JsonParser()
              .parse(response.body().string())
              .getAsJsonObject()
              .get("access_token")
              .getAsString();

      LOG.info("Che Service account token has been successfully retrieved");
    } catch (IOException e) {
      throw new RuntimeException(
          "Service account token retrieving failed. Error: " + e.getMessage(), e);
    }
  }

  public Config getWorkspacesOpenshiftConfig(Subject subject) throws InfrastructureException {
    checkSubject(subject);
    UserCheTenantData cheTenantData = getUserCheTenantData(subject);
    checkClusterCapacity(cheTenantData);

    ConfigBuilder configBuilder =
        new ConfigBuilder().withNamespace(cheTenantData.getNamespace()).withTrustCerts(true);

    String userId = subject.getUserId();

    if (cheServiceAccountTokenToggle.useCheServiceAccountToken(userId)) {
      LOG.debug("Using Che SA token for '{}'", userId);
      String osoProxyUrl = multiClusterOpenShiftProxy.getUrlWithIdentityIdQueryParameter(userId);
      configBuilder.withMasterUrl(osoProxyUrl).withOauthToken(cheServiceAccountToken);
    } else {
      String osoProxyUrl = multiClusterOpenShiftProxy.getUrl();
      configBuilder.withMasterUrl(osoProxyUrl).withOauthToken(subject.getToken());
    }

    return configBuilder.build();
  }

  public String getWorkspacesOpenshiftNamespace(Subject subject) throws InfrastructureException {
    checkSubject(subject);

    return getUserCheTenantData(subject).getNamespace();
  }

  private void checkSubject(Subject subject) throws InfrastructureException {
    if (subject == null) {
      throw new InfrastructureException("No Subject is found to perform this action");
    }
    if (subject == Subject.ANONYMOUS) {
      throw new InfrastructureException(
          "The anonymous subject is used, and won't be able to perform this action");
    }
  }

  private void checkClusterCapacity(UserCheTenantData data) throws InfrastructureException {
    if (data.isClusterCapacityExhausted() == true) {
      throw new InfrastructureException(
          "Cannot start a workspace. OpenShift Online cluster is currently out of capacity");
    }
  }

  private UserCheTenantData getUserCheTenantData(Subject subject) throws InfrastructureException {
    UserCheTenantData tenantData = tenantDataProvider.getUserCheTenantData(subject, "che");
    return new UserCheTenantData(
        tenantData.getNamespace(),
        multiClusterOpenShiftProxy.getUrl(),
        tenantData.getRouteBaseSuffix(),
        tenantData.isClusterCapacityExhausted());
  }
}
