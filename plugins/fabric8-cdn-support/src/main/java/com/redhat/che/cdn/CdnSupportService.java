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
package com.redhat.che.cdn;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.of;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.redhat.che.cdn.plugin.model.Container;
import com.redhat.che.cdn.plugin.model.PluginMeta;
import com.redhat.che.cdn.plugin.model.Spec;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.UriBuilder;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.core.util.LineConsumer;
import org.eclipse.che.api.core.util.ListLineConsumer;
import org.eclipse.che.api.core.util.ProcessUtil;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.wsplugins.PluginFQNParser;
import org.eclipse.che.api.workspace.server.wsplugins.model.PluginFQN;
import org.eclipse.che.commons.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Path("cdn-support")
public class CdnSupportService extends Service {
  private static final Logger LOG = LoggerFactory.getLogger(CdnSupportService.class);
  private static final ObjectMapper JSON_PARSER = new ObjectMapper(new JsonFactory());
  private static final String SKOPEO_BINARY = "skopeo";
  private static final String[] SKOPEO_HELP_ARGS = new String[] {"--help"};
  private static final long SKOPEO_HELP_TIMEOUT_SECONDS = 2;
  private static final String SKOPEO_INSPECT_ARG = "inspect";
  private static final String SKOPEO_IMAGE_PREFIX = "docker://";
  private static final long SKOPEO_INSPECT_TIMEOUT_SECONDS = 10;
  private static final String LABEL_NAME = "che-plugin.cdn.artifacts";
  private static final String META_YAML = "meta.yaml";

  private static final CompletableFuture<?>[] FUTURE_ARRAY = new CompletableFuture<?>[0];
  private static final CommandRunner RUNNER = new CommandRunner();

  private final String editorToPrefetch;
  private final UriBuilder pluginRegistry;
  private final CommandRunner commandRunner;
  private final PluginFQNParser pluginFQNParser;
  private final HttpYamlDownloader yamlDownloader;
  @VisibleForTesting String dockerImage = null;

  @Inject
  public CdnSupportService(
      PluginFQNParser pluginFQNParser,
      HttpYamlDownloader yamlDownloader,
      @Named("che.workspace.plugin_registry_url") String registryUrl,
      @Nullable @Named("che.fabric8.cdn.prefetch.editor") String editorToPrefetch) {
    this(RUNNER, pluginFQNParser, yamlDownloader, registryUrl, editorToPrefetch);
  }

  @VisibleForTesting
  CdnSupportService(
      CommandRunner commandRunner,
      PluginFQNParser pluginFQNParser,
      HttpYamlDownloader yamlDownloader,
      @Named("che.workspace.plugin_registry_url") String registryUrl,
      @Nullable @Named("che.fabric8.cdn.prefetch.editor") String editorToPrefetch) {
    this.editorToPrefetch = editorToPrefetch;
    this.commandRunner = commandRunner;
    this.pluginFQNParser = pluginFQNParser;
    this.yamlDownloader = yamlDownloader;
    this.pluginRegistry = UriBuilder.fromUri(registryUrl).path("plugins");

    // Test that the skopeo process is available

    try {
      commandRunner.runCommand(
          SKOPEO_BINARY,
          SKOPEO_HELP_ARGS,
          null,
          SKOPEO_HELP_TIMEOUT_SECONDS,
          TimeUnit.SECONDS,
          null,
          null);
    } catch (IOException e) {
      throw new RuntimeException(
          "The `skopeo` command is not available. Please check that is has been correctly installed in the Che server Docker image",
          e);
    } catch (ExecutionException | InterruptedException | TimeoutException e) {
      LOG.warn("Exception during the `skopeo --help` command execution", e);
    }
  }

  @GET
  @Path("paths")
  @Produces(APPLICATION_JSON)
  public String getPaths() throws Exception {
    if (editorToPrefetch == null) {
      throw new NotFoundException("No editor is configured for CDN resource pre-fetching");
    }

    if (dockerImage == null) {
      PluginMeta editorMeta = getEditorMeta();
      dockerImage = getDockerImage(editorMeta);
    }

    JsonNode json = inspectDockerImage();
    return json.path("Labels").path(LABEL_NAME).asText("[]");
  }

  private String getDockerImage(final PluginMeta editorMeta) throws ServerException {
    if (editorMeta == null) {
      throw new ServerException(format("Plugin meta not found for '%s'", editorToPrefetch));
    }

    Spec spec = editorMeta.getSpec();
    if (spec == null) {
      throw new ServerException(format("Plugin spec not found for '%s'", editorToPrefetch));
    }

    List<Container> containers = spec.getContainers();
    if (containers == null || containers.isEmpty()) {
      throw new ServerException(
          format("No containers found in the plugin spec for '%s'", editorToPrefetch));
    }

    if (containers.size() != 1) {
      throw new ServerException(
          format("More than one container found in the plugin spec for '%s'", editorToPrefetch));
    }

    String image = containers.get(0).getImage();
    if (image == null || image.isEmpty()) {
      throw new ServerException(
          format("Image is not defined in the container spec for '%s'", editorToPrefetch));
    }
    return image;
  }

  private JsonNode inspectDockerImage()
      throws IOException, TimeoutException, InterruptedException, ExecutionException,
          ServerException {
    LOG.debug("Running {} on image {}", SKOPEO_BINARY, dockerImage);
    final ListLineConsumer out = commandRunner.newOutputConsumer();
    final ListLineConsumer err = commandRunner.newErrorConsumer();
    Process skopeoInspect =
        commandRunner.runCommand(
            SKOPEO_BINARY,
            new String[] {SKOPEO_INSPECT_ARG, SKOPEO_IMAGE_PREFIX + dockerImage},
            null,
            SKOPEO_INSPECT_TIMEOUT_SECONDS,
            TimeUnit.SECONDS,
            out,
            err);
    if (skopeoInspect.exitValue() != 0) {
      String message =
          format(
              "%s failed when trying to retrieve the CDN label of docker image %s - exit code: %d - error output: %s",
              SKOPEO_BINARY, dockerImage, skopeoInspect.exitValue(), err.getText());
      LOG.warn(message);
      throw new ServerException(message);
    }
    String skopeoOutput = out.getText();
    LOG.debug("Result of running skopeo on image {}: {}", dockerImage, skopeoOutput);

    JsonNode json = JSON_PARSER.readTree(skopeoOutput);
    return json;
  }

  private PluginMeta getEditorMeta() throws InfrastructureException {
    PluginFQN pluginFQN = pluginFQNParser.parsePluginFQN(editorToPrefetch);
    UriBuilder uriBuilder =
        pluginFQN.getRegistry() == null
            ? pluginRegistry.clone()
            : UriBuilder.fromUri(pluginFQN.getRegistry());
    URI editorURI = uriBuilder.path(pluginFQN.getId()).path(META_YAML).build();
    try {
      return yamlDownloader.getYamlResponseAndParse(editorURI);
    } catch (IOException e) {
      throw new InfrastructureException(
          String.format(
              "Failed to download editor meta.yaml for '%s': Error: %s",
              editorToPrefetch, e.getMessage()));
    }
  }

  @VisibleForTesting
  static class CommandRunner {
    @VisibleForTesting
    Process runCommand(
        String command,
        String[] arguments,
        File directory,
        long timeout,
        TimeUnit timeUnit,
        LineConsumer outputConsumer,
        LineConsumer errorConsumer)
        throws IOException, TimeoutException, InterruptedException, ExecutionException {
      final String[] commandLine = new String[arguments.length + 1];
      Lists.asList(command, arguments).toArray(commandLine);
      ProcessBuilder processBuilder = new ProcessBuilder();
      if (directory != null) {
        processBuilder.directory(directory);
      }
      processBuilder.command(commandLine);
      LOG.debug("Command: {}", processBuilder.command());
      LOG.debug("Directory: {}", processBuilder.directory());
      Process process = processBuilder.start();
      CompletableFuture<Void> readers =
          allOf(
              of(outputConsumer, errorConsumer)
                  .map(
                      (LineConsumer consumer) -> {
                        return runAsync(
                            () -> {
                              if (consumer != null) {
                                InputStream is =
                                    consumer == outputConsumer
                                        ? process.getInputStream()
                                        : process.getErrorStream();
                                // consume logs until process ends
                                try (BufferedReader inputReader =
                                    new BufferedReader(new InputStreamReader(is))) {
                                  String line;
                                  while ((line = inputReader.readLine()) != null) {
                                    consumer.writeLine(line);
                                  }
                                } catch (IOException e) {
                                  LOG.error(
                                      format(
                                          "Failed to complete reading of the process '%s' output or error due to occurred error",
                                          Joiner.on(" ").join(commandLine)),
                                      e);
                                }
                              }
                            });
                      })
                  .collect(toList())
                  .toArray(FUTURE_ARRAY));
      try {
        if (!process.waitFor(timeout, timeUnit)) {
          try {
            ProcessUtil.kill(process);
          } catch (RuntimeException x) {
            LOG.error(
                "An error occurred while killing process '{}'", Joiner.on(" ").join(commandLine));
          }
          throw new TimeoutException(
              format(
                  "Process '%s' was terminated by timeout %s %s.",
                  Joiner.on(" ").join(commandLine), timeout, timeUnit.name().toLowerCase()));
        }
      } finally {
        readers.get(2, TimeUnit.SECONDS);
      }

      return process;
    }

    @VisibleForTesting
    ListLineConsumer newOutputConsumer() {
      return new ListLineConsumer();
    }

    @VisibleForTesting
    ListLineConsumer newErrorConsumer() {
      return new ListLineConsumer();
    }
  }
}
