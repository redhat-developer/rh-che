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
package org.eclipse.che.plugin.languageserver.bayesian.server.launcher;

import static org.slf4j.LoggerFactory.getLogger;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import javax.inject.Named;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.languageserver.DefaultInstanceProvider;
import org.eclipse.che.api.languageserver.LanguageServerConfig;
import org.eclipse.che.api.languageserver.ProcessCommunicationProvider;
import org.eclipse.che.api.project.server.impl.RootDirPathProvider;
import org.eclipse.che.plugin.json.inject.JsonModule;
import org.eclipse.che.plugin.languageserver.bayesian.BayesianLanguageServerModule;
import org.slf4j.Logger;

/**
 * @author Evgen Vidolob
 * @author Anatolii Bazko
 */
@Singleton
public class BayesianLanguageServerConfig implements LanguageServerConfig {
  private static final Logger LOG = getLogger(BayesianLanguageServerConfig.class);

  private final Path launchScript;
  private final HttpJsonRequestFactory httpJsonFactory;
  private final RootDirPathProvider rootDirPathProvider;
  private final String apiEndpoint;

  @Inject
  public BayesianLanguageServerConfig(
      RootDirPathProvider rootDirPathProvider,
      HttpJsonRequestFactory httpJsonFactory,
      @Named("che.api") String apiEndpoint) {
    this.httpJsonFactory = httpJsonFactory;
    this.rootDirPathProvider = rootDirPathProvider;
    this.apiEndpoint = apiEndpoint;
    launchScript = Paths.get(System.getenv("HOME"), "che/ls-bayesian/launch.sh");
  }

  @Override
  public String getProjectsRoot() {
    return rootDirPathProvider.get();
  }

  @Override
  public RegexProvider getRegexpProvider() {
    return new RegexProvider() {
      @Override
      public Map<String, String> getLanguageRegexes() {
        return ImmutableMap.<String, String>builder()
            .put(BayesianLanguageServerModule.TXT_LANGUAGE_ID, ".*requirements\\.txt")
            .put(JsonModule.LANGUAGE_ID, ".*package\\.json")
            .put("pom", ".*pom\\.xml")
            .build();
      }

      @Override
      public Set<String> getFileWatchPatterns() {
        return ImmutableSet.of();
      }
    };
  }

  @Override
  public CommunicationProvider getCommunicationProvider() {
    String recommenderToken;

    try {
      String endpoint = apiEndpoint + "/bayesian/token";
      LOG.debug("Retrieving the Bayesian recommender token from API : {}", endpoint);
      recommenderToken = httpJsonFactory.fromUrl(endpoint).request().asString();
    } catch (Exception e) {
      throw new IllegalStateException("Can't start Bayesian language server", e);
    }

    String launchCommand =
        "export RECOMMENDER_API_TOKEN=\"" + recommenderToken + "\" && " + launchScript.toString();

    ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", launchCommand);
    processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
    processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
    processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

    return new ProcessCommunicationProvider(
        processBuilder, "org.eclipse.che.plugin.bayesian.languageserver");
  }

  @Override
  public InstanceProvider getInstanceProvider() {
    return DefaultInstanceProvider.getInstance();
  }

  @Override
  public InstallerStatusProvider getInstallerStatusProvider() {
    return new InstallerStatusProvider() {
      @Override
      public boolean isSuccessfullyInstalled() {
        return launchScript.toFile().exists();
      }

      @Override
      public String getCause() {
        return isSuccessfullyInstalled() ? null : "Launch script file does not exist";
      }
    };
  }
}
