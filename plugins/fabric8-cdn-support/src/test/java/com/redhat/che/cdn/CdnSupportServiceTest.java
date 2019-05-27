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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.core.UriBuilder;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.util.ListLineConsumer;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.wsplugins.PluginFQNParser;
import org.eclipse.che.api.workspace.server.wsplugins.model.ExtendedPluginFQN;
import org.eclipse.che.api.workspace.server.wsplugins.model.PluginMeta;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class CdnSupportServiceTest {
  private static final String EDITOR_REF = "eclipse/che-theia/next";
  private static final String EDITOR_URL = "http://editorURL";
  private static final String IMAGE_REF = "imageRef";
  private static final String DEFAULT_REGISTRY_URL = "https://che-plugin-registry.openshift.io/v2";
  private static final String DOWNLOAD_ERROR = "Error downloading";
  private static final URI PLUGIN_URL =
      UriBuilder.fromUri(DEFAULT_REGISTRY_URL + "/plugins/" + EDITOR_REF + "/").build();
  private static final ExtendedPluginFQN PLUGIN_FQN =
      new ExtendedPluginFQN(null, EDITOR_REF, "publisher", "name", "version");

  private static final String DOWNLOAD_EXCEPTION_MESSAGE =
      "Failed to download editor meta.yaml for '.*': Error: " + DOWNLOAD_ERROR;
  private static final String SERVER_EXCEPTION_MESSAGE =
      "URL of editor '" + EDITOR_REF + "' is null";

  @Mock private CdnSupportService.CommandRunner commandRunner;
  @Mock private URLConnection urlConnection;
  @Mock private PluginMeta pluginMeta;
  @Mock private Process tarProcess;
  @Mock private Process skopeoHelpProcess;
  @Mock private Process skopeoInspectProcess;
  @Mock private ListLineConsumer skopeoOutputConsumer;
  @Mock private ListLineConsumer skopeoErrorConsumer;
  @Mock private ListLineConsumer tarErrorConsumer;
  @Mock private HttpYamlDownloader yamlDownloader;
  @Mock private PluginFQNParser pluginFQNParser;

  private CdnSupportService service;

  @BeforeClass
  public void registerURLHandler() {
    URL.setURLStreamHandlerFactory(
        new URLStreamHandlerFactory() {

          @Override
          public URLStreamHandler createURLStreamHandler(String protocol) {
            if ("http".equals(protocol)) {
              return new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL u) throws IOException {
                  return urlConnection;
                }
              };
            } else {
              return null;
            }
          }
        });
  }

  @BeforeMethod
  public void setUp() throws Exception {
    service = null;
    when(pluginFQNParser.parsePluginFQN(any())).thenReturn(PLUGIN_FQN);
  }

  private void setupURLConnectionInputStream(String resourceName) throws IOException {
    InputStream is = this.getClass().getResourceAsStream(resourceName);
    doReturn(is).when(urlConnection).getInputStream();
  }

  @Test(
      expectedExceptions = {RuntimeException.class},
      expectedExceptionsMessageRegExp =
          "The `skopeo` command is not available. Please check that is has been correctly installed in the Che server Docker image")
  public void throwAtStartIfNoSkopeoBinary() throws Exception {
    doThrow(IOException.class)
        .when(commandRunner)
        .runCommand("skopeo", new String[] {"--help"}, null, 2, TimeUnit.SECONDS, null, null);
    new CdnSupportService(commandRunner, pluginFQNParser, yamlDownloader, DEFAULT_REGISTRY_URL, "");
  }

  @Test(
      expectedExceptions = {NotFoundException.class},
      expectedExceptionsMessageRegExp = "No editor is configured for CDN resource pre-fetching")
  public void throwWhenNoPreferredEditor() throws Exception {
    service =
        new CdnSupportService(
            commandRunner, pluginFQNParser, yamlDownloader, DEFAULT_REGISTRY_URL, null);
    service.getPaths();
  }

  @Test(
      expectedExceptions = {InfrastructureException.class},
      expectedExceptionsMessageRegExp = DOWNLOAD_EXCEPTION_MESSAGE)
  public void throwWhenEditorNotFound() throws Exception {
    doThrow(new IOException(DOWNLOAD_ERROR)).when(yamlDownloader).getYamlResponseAndParse(any());
    service =
        new CdnSupportService(
            commandRunner, pluginFQNParser, yamlDownloader, DEFAULT_REGISTRY_URL, "unknownEditor");
    service.getPaths();
  }

  @Test(
      expectedExceptions = {ServerException.class},
      expectedExceptionsMessageRegExp = SERVER_EXCEPTION_MESSAGE)
  public void throwWhenEditorPluginUrlIsNull() throws Exception {
    doReturn(pluginMeta).when(yamlDownloader).getYamlResponseAndParse(any());
    doReturn(null).when(pluginMeta).getUrl();
    service =
        new CdnSupportService(
            commandRunner, pluginFQNParser, yamlDownloader, DEFAULT_REGISTRY_URL, EDITOR_REF);

    service.getPaths();
  }

  @Test(
      expectedExceptions = {ServerException.class},
      expectedExceptionsMessageRegExp =
          "skopeo failed when trying to retrieve the CDN label of docker image imageRef - exit code: 1 - error output: skopeo error output")
  public void throwWhenSkopeoFailsWithNonZeroCode() throws Exception {
    doReturn(pluginMeta).when(yamlDownloader).getYamlResponseAndParse(any());

    doReturn(EDITOR_URL).when(pluginMeta).getUrl();

    lenient()
        .when(commandRunner.runCommand(eq("skopeo"), any(), any(), anyLong(), any(), any(), any()))
        .thenReturn(skopeoHelpProcess, skopeoInspectProcess);
    doReturn(0).when(skopeoHelpProcess).exitValue();
    doReturn(1).when(skopeoInspectProcess).exitValue();
    when(commandRunner.newOutputConsumer()).thenReturn(skopeoOutputConsumer);
    when(commandRunner.newErrorConsumer()).thenReturn(skopeoErrorConsumer);
    when(skopeoErrorConsumer.getText()).thenReturn("skopeo error output");

    service =
        new CdnSupportService(
            commandRunner, pluginFQNParser, yamlDownloader, DEFAULT_REGISTRY_URL, EDITOR_REF);
    service.editorDefinitionUrl = EDITOR_URL;
    service.dockerImage = IMAGE_REF;

    service.getPaths();
  }

  @Test
  public void reuseExistingImageRefAndReturnLabelWhenSkopeoSucceeds() throws Exception {
    doReturn(pluginMeta).when(yamlDownloader).getYamlResponseAndParse(any());

    doReturn(EDITOR_URL).when(pluginMeta).getUrl();

    lenient()
        .when(commandRunner.runCommand(eq("skopeo"), any(), any(), anyLong(), any(), any(), any()))
        .thenReturn(skopeoHelpProcess, skopeoInspectProcess);
    doReturn(0).when(skopeoInspectProcess).exitValue();
    when(commandRunner.newOutputConsumer()).thenReturn(skopeoOutputConsumer);
    when(commandRunner.newErrorConsumer()).thenReturn(skopeoErrorConsumer);
    when(skopeoOutputConsumer.getText())
        .thenReturn("{\"Labels\": { \"che-plugin.cdn.artifacts\": \"cdnJsonContent\" }}");

    service =
        new CdnSupportService(
            commandRunner, pluginFQNParser, yamlDownloader, DEFAULT_REGISTRY_URL, EDITOR_REF);
    service.editorDefinitionUrl = EDITOR_URL;
    service.dockerImage = IMAGE_REF;

    assertEquals(service.getPaths(), "cdnJsonContent");

    verify(yamlDownloader, times(1)).getYamlResponseAndParse(PLUGIN_URL);
    verify(commandRunner, never())
        .runCommand(eq("tar"), any(), any(), anyLong(), any(), any(), any());
  }

  @Test(
      expectedExceptions = {ServerException.class},
      expectedExceptionsMessageRegExp =
          "Tar command failed with error status: 1 and error log: tar error output content")
  public void searchForImageRefAndThrowWhenTarFails() throws Exception {
    doReturn(pluginMeta).when(yamlDownloader).getYamlResponseAndParse(any());

    doReturn(EDITOR_URL).when(pluginMeta).getUrl();

    lenient()
        .when(commandRunner.runCommand(eq("skopeo"), any(), any(), anyLong(), any(), any(), any()))
        .thenReturn(skopeoHelpProcess);
    doReturn(0).when(skopeoHelpProcess).exitValue();
    lenient()
        .when(commandRunner.runCommand(eq("tar"), any(), any(), anyLong(), any(), any(), any()))
        .thenReturn(tarProcess);
    doReturn(1).when(tarProcess).exitValue();
    when(commandRunner.newErrorConsumer()).thenReturn(tarErrorConsumer);
    when(tarErrorConsumer.getText()).thenReturn("tar error output content");

    setupURLConnectionInputStream("/che-editor-plugin.tar.gz");
    service =
        new CdnSupportService(
            commandRunner, pluginFQNParser, yamlDownloader, DEFAULT_REGISTRY_URL, EDITOR_REF);

    try {
      assertEquals(service.getPaths(), "{}");
    } catch (Exception e) {
      throw e;
    } finally {
      verify(yamlDownloader, times(1)).getYamlResponseAndParse(PLUGIN_URL);
      verify(commandRunner, times(1))
          .runCommand(eq("skopeo"), any(), any(), anyLong(), any(), any(), any());
      verify(commandRunner, times(1))
          .runCommand(eq("tar"), any(), any(), anyLong(), any(), any(), any());
    }
  }

  @Test
  public void searchForImageRefAndReturnLabelWhenSkopeoSucceeds() throws Exception {
    doReturn(pluginMeta).when(yamlDownloader).getYamlResponseAndParse(any());

    doReturn(EDITOR_URL).when(pluginMeta).getUrl();

    lenient()
        .when(commandRunner.runCommand(eq("skopeo"), any(), any(), anyLong(), any(), any(), any()))
        .thenReturn(skopeoHelpProcess, skopeoInspectProcess);
    doReturn(0).when(skopeoHelpProcess).exitValue();
    doReturn(0).when(skopeoInspectProcess).exitValue();
    lenient()
        .when(commandRunner.runCommand(eq("tar"), any(), any(), anyLong(), any(), any(), any()))
        .thenCallRealMethod();
    doReturn(0).when(tarProcess).exitValue();
    when(commandRunner.newOutputConsumer()).thenReturn(skopeoOutputConsumer);
    when(commandRunner.newErrorConsumer()).thenReturn(tarErrorConsumer, skopeoErrorConsumer);
    when(skopeoErrorConsumer.getText()).thenReturn("skopeo error output content");
    when(skopeoOutputConsumer.getText())
        .thenReturn("{\"Labels\": { \"che-plugin.cdn.artifacts\": \"cdnJsonContent\" }}");

    setupURLConnectionInputStream("/che-editor-plugin.tar.gz");
    service =
        new CdnSupportService(
            commandRunner, pluginFQNParser, yamlDownloader, DEFAULT_REGISTRY_URL, EDITOR_REF);

    assertEquals(service.getPaths(), "cdnJsonContent");

    verify(yamlDownloader, times(1)).getYamlResponseAndParse(PLUGIN_URL);
    verify(commandRunner, times(2))
        .runCommand(eq("skopeo"), any(), any(), anyLong(), any(), any(), any());
    verify(commandRunner, times(1))
        .runCommand(eq("tar"), any(), any(), anyLong(), any(), any(), any());
  }
}
