/*
 * Copyright (c) 2016-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.plugin.languageserver.bayesian.server.launcher;

import static org.slf4j.LoggerFactory.getLogger;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.inject.Named;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.languageserver.exception.LanguageServerException;
import org.eclipse.che.api.languageserver.launcher.LanguageServerLauncherTemplate;
import org.eclipse.che.api.languageserver.registry.DocumentFilter;
import org.eclipse.che.api.languageserver.registry.LanguageServerDescription;
import org.eclipse.che.plugin.json.inject.JsonModule;
import org.eclipse.che.plugin.languageserver.bayesian.BayesianLanguageServerModule;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.Logger;

/**
 * @author Evgen Vidolob
 * @author Anatolii Bazko
 */
@Singleton
public class BayesianLanguageServerLauncher extends LanguageServerLauncherTemplate {
  private static final Logger LOG = getLogger(BayesianLanguageServerLauncher.class);

  private static final LanguageServerDescription DESCRIPTION = createServerDescription();

  private final Path launchScript;
  private final HttpJsonRequestFactory httpJsonFactory;
  private final String apiEndpoint;

  @Inject
  public BayesianLanguageServerLauncher(
      HttpJsonRequestFactory httpJsonFactory, @Named("che.api") String apiEndpoint) {
    this.httpJsonFactory = httpJsonFactory;
    this.apiEndpoint = apiEndpoint;
    launchScript = Paths.get(System.getenv("HOME"), "che/ls-bayesian/launch.sh");
  }

  @Override
  public boolean isAbleToLaunch() {
    return Files.exists(launchScript);
  }

  protected LanguageServer connectToLanguageServer(
      final Process languageServerProcess, LanguageClient client) {
    Launcher<LanguageServer> launcher =
        Launcher.createLauncher(
            client,
            LanguageServer.class,
            languageServerProcess.getInputStream(),
            languageServerProcess.getOutputStream());
    launcher.startListening();
    return launcher.getRemoteProxy();
  }

  protected Process startLanguageServerProcess(String projectPath) throws LanguageServerException {
    String recommenderToken = null;

    try {
      String endpoint = apiEndpoint + "/bayesian/token";
      LOG.debug("Retrieving the Bayesian recommender token from API : {}", endpoint);
      recommenderToken = httpJsonFactory.fromUrl(endpoint).request().asString();
    } catch (Exception e) {
      throw new LanguageServerException("Can't start Bayesian language server", e);
    }

    String launchCommand =
        "export RECOMMENDER_API_TOKEN=\"" + recommenderToken + "\" && " + launchScript.toString();

    ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", launchCommand);
    processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
    processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
    processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
    try {
      return processBuilder.start();
    } catch (IOException e) {
      throw new LanguageServerException("Can't start Bayesian language server", e);
    }
  }

  public LanguageServerDescription getDescription() {
    return DESCRIPTION;
  }

  private static LanguageServerDescription createServerDescription() {
    return new LanguageServerDescription(
        "org.eclipse.che.plugin.bayesian.languageserver", //
        null, //
        Arrays.asList(
            new DocumentFilter(
                BayesianLanguageServerModule.TXT_LANGUAGE_ID, //
                "requirements\\.txt", //
                null), //
            new DocumentFilter(
                JsonModule.LANGUAGE_ID, //
                "package\\.json", //
                null),
            new DocumentFilter("pom", "pom\\.xml", null)) //
        );
  }
}
