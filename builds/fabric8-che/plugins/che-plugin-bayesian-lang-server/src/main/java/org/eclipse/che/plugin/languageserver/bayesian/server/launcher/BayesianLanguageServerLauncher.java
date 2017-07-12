/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.plugin.languageserver.bayesian.server.launcher;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.languageserver.exception.LanguageServerException;
import org.eclipse.che.api.languageserver.launcher.LanguageServerLauncherTemplate;
import org.eclipse.che.api.languageserver.registry.DocumentFilter;
import org.eclipse.che.api.languageserver.registry.LanguageServerDescription;
import org.eclipse.che.plugin.json.inject.JsonModule;
import org.eclipse.che.plugin.languageserver.bayesian.BayesianLanguageServerModule;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * @author Evgen Vidolob
 * @author Anatolii Bazko
 */
@Singleton
public class BayesianLanguageServerLauncher extends LanguageServerLauncherTemplate {

    private static final LanguageServerDescription DESCRIPTION = createServerDescription();

    private final Path                             launchScript;

    @Inject
    public BayesianLanguageServerLauncher() {
        launchScript = Paths.get(System.getenv("HOME"), "che/ls-bayesian/launch.sh");
    }

    @Override
    public boolean isAbleToLaunch() {
        return Files.exists(launchScript);
    }

    protected LanguageServer connectToLanguageServer(final Process languageServerProcess, LanguageClient client) {
        Launcher<LanguageServer> launcher = Launcher.createLauncher(client, LanguageServer.class,
                                                                    languageServerProcess.getInputStream(),
                                                                    languageServerProcess.getOutputStream());
        launcher.startListening();
        return launcher.getRemoteProxy();
    }

    protected Process startLanguageServerProcess(String projectPath) throws LanguageServerException {
        ProcessBuilder processBuilder = new ProcessBuilder(launchScript.toString());
        // dev: inject your keycloak token here: (retrieve it from openshift.io localStorage.getItem("auth_token"))
        // ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", //
        //                                                    "-c", //
        //                                                    "export RECOMMENDER_API_TOKEN=xxx && " + //
        //                                                        launchScript.toString());
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
        return new LanguageServerDescription("org.eclipse.che.plugin.bayesian.languageserver",//
                                             null,//
                                             Arrays.asList(new DocumentFilter(BayesianLanguageServerModule.TXT_LANGUAGE_ID,//
                                                                              "requirements\\.txt",//
                                                                              null),//
                                                           new DocumentFilter(JsonModule.LANGUAGE_ID,//
                                                                              "package\\.json",//
                                                                              null)//
                                             // TODO pom.xml
                                             )//
        );
    }

}
