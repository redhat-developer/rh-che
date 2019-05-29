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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.CharStreams;
import com.redhat.che.cdn.plugin.model.PluginMeta;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;

public class HttpYamlDownloader {
  private static final ObjectMapper YAML_PARSER = new ObjectMapper(new YAMLFactory());

  public PluginMeta getYamlResponseAndParse(URI uri) throws IOException {
    HttpURLConnection httpURLConnection = null;
    try {
      httpURLConnection = (HttpURLConnection) uri.toURL().openConnection();
      if (httpURLConnection.getResponseCode() != 200) {
        throw new IOException(
            String.format(
                "Could not get editor meta from URI '%s': Error: '%s'",
                uri, getError(httpURLConnection)));
      }

      return parseYamlResponseStreamAndClose(httpURLConnection.getInputStream());
    } finally {
      if (httpURLConnection != null) {
        httpURLConnection.disconnect();
      }
    }
  }

  private String getError(HttpURLConnection httpURLConnection) throws IOException {
    try (InputStreamReader isr = new InputStreamReader(httpURLConnection.getInputStream())) {
      return CharStreams.toString(isr);
    }
  }

  protected PluginMeta parseYamlResponseStreamAndClose(InputStream inputStream) throws IOException {
    try (InputStreamReader reader = new InputStreamReader(inputStream)) {
      return YAML_PARSER.readValue(reader, PluginMeta.class);
    } catch (IOException e) {
      throw new IOException(
          "Internal server error. Unexpected response body received from Che plugin registry API."
              + e.getLocalizedMessage(),
          e);
    }
  }
}
