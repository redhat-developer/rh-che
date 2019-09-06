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

import static org.testng.Assert.assertEquals;

import com.redhat.che.cdn.plugin.model.Container;
import com.redhat.che.cdn.plugin.model.PluginMeta;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.testng.annotations.Test;

public class HttpYamlDownloaderTest {
  private final String EDITOR_PLUGIN_URL_ON_PROD =
      "https://che-plugin-registry.openshift.io/v3/plugins/eclipse/che-theia/next/meta.yaml";
  private final String EDITOR_PLUGIN_URL_ON_PROD_PREVIEW =
      "https://che-plugin-registry.prod-preview.openshift.io/v3/plugins/eclipse/che-theia/next/meta.yaml";
  private final String EDITOR_PLUGIN_VERSION = "next";
  private final String EDITOR_PLUGIN_PUBLISHER = "eclipse";
  private final String EDITOR_PLUGIN_TYPE = "Che Editor";
  private final String EDITOR_CONTAINER_NAME = "theia-ide";
  private final String PROD_EDITOR_CONTAINER_IMAGE = "eclipse/che-theia:next";
  private final String PREVIEW_EDITOR_CONTAINER_IMAGE = "docker.io/eclipse/che-theia:next";

  @Test
  public void getYamlResponseAndParseOnProd() throws URISyntaxException, IOException {
    getYamlResponseAndParse(EDITOR_PLUGIN_URL_ON_PROD, PROD_EDITOR_CONTAINER_IMAGE);
  }

  @Test
  public void getYamlResponseAndParseOnProdPreview() throws URISyntaxException, IOException {
    getYamlResponseAndParse(EDITOR_PLUGIN_URL_ON_PROD_PREVIEW, PREVIEW_EDITOR_CONTAINER_IMAGE);
  }

  private void getYamlResponseAndParse(
      final String editorPluginUrl, final String editorContainerImage)
      throws URISyntaxException, IOException {
    HttpYamlDownloader httpYamlDownloader = new HttpYamlDownloader();
    PluginMeta pluginMeta = httpYamlDownloader.getYamlResponseAndParse(new URI(editorPluginUrl));

    assertEquals(pluginMeta.getVersion(), EDITOR_PLUGIN_VERSION, "Plugin version is incorrect");
    assertEquals(
        pluginMeta.getPublisher(), EDITOR_PLUGIN_PUBLISHER, "Plugin publisher is incorrect");
    assertEquals(pluginMeta.getType(), EDITOR_PLUGIN_TYPE, "Plugin type is incorrect");

    List<Container> containers = pluginMeta.getSpec().getContainers();
    assertEquals(containers.size(), 1, "There should be 1 'theia-ide' container");

    Container container = containers.get(0);
    assertEquals(container.getName(), EDITOR_CONTAINER_NAME, "Editor container name is incorrect");
    assertEquals(container.getImage(), editorContainerImage, "Editor container image is incorrect");
  }
}
