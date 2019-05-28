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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.che.api.workspace.server.wsplugins.model.PluginMeta;
import org.testng.annotations.Test;

public class HttpYamlDownloaderTest {
  private final String EDITOR_PLUGIN_URL_ON_PROD =
      "https://che-plugin-registry.openshift.io/v2/plugins/eclipse/che-theia/next/meta.yaml";
  private final String EDITOR_PLUGIN_URL_ON_PROD_PREVIEW =
      "https://che-plugin-registry.prod-preview.openshift.io/v2/plugins/eclipse/che-theia/next/meta.yaml";
  private final String EDITOR_PLUGIN_VERSION = "next";
  private final String EDITOR_PLUGIN_REPOSITORY = "https://github.com/eclipse/che-theia";
  private final String EDITOR_PLUGIN_CATEGORY = "Editor";

  @Test
  public void getYamlResponseAndParseOnProd() throws URISyntaxException, IOException {
    getYamlResponseAndParse(EDITOR_PLUGIN_URL_ON_PROD);
  }

  @Test
  public void getYamlResponseAndParseOnProdPreview() throws URISyntaxException, IOException {
    getYamlResponseAndParse(EDITOR_PLUGIN_URL_ON_PROD_PREVIEW);
  }

  private void getYamlResponseAndParse(final String editorPluginUrl)
      throws URISyntaxException, IOException {
    HttpYamlDownloader httpYamlDownloader = new HttpYamlDownloader();
    PluginMeta pluginMeta = httpYamlDownloader.getYamlResponseAndParse(new URI(editorPluginUrl));
    assertEquals(pluginMeta.getCategory(), EDITOR_PLUGIN_CATEGORY, "Plugin category is incorrect");
    assertEquals(pluginMeta.getVersion(), EDITOR_PLUGIN_VERSION, "Plugin version is incorrect");
    assertEquals(
        pluginMeta.getRepository(), EDITOR_PLUGIN_REPOSITORY, "Plugin repository is incorrect");
  }
}
