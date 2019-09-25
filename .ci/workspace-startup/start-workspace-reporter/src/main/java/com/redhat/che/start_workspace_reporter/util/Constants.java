/*
 * Copyright (c) 2018-2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.che.start_workspace_reporter.util;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class Constants {

  private static final Long ONE_DAY_MILLIS = 86_400_000L;
  private static final Long SEVEN_DAYS_LILLIS = 604_800_000L;
  public static final Long TIMESTAMP_NOW = (System.currentTimeMillis()) / 1000;
  public static final Long TIMESTAMP_YESTERDAY =
      (System.currentTimeMillis() - ONE_DAY_MILLIS) / 1000;
  public static final Long TIMESTAMP_LAST_SEVEN_DAYS =
      (System.currentTimeMillis() - ONE_DAY_MILLIS - SEVEN_DAYS_LILLIS) / 1000;

  public static final int ZABBIX_HISTORY_GET_ALL_ENTRIES = 0;

  public static final String SLACK_SUCCESS_COLOR = "#2EB886";
  public static final String SLACK_UNSTABLE_COLOR = "#FFAA00";
  public static final String SLACK_BROKEN_COLOR = "#FF0000";
  public static final float SLACK_BROKEN_PERCENTAGE = 5f; // above value
  public static final float SLACK_UNSTABLE_PERCENTAGE = 1f; // above value
  public static final String SUFFIX_AVG = "_AVG";
  public static final String SUFFIX_MAX = "_MAX";

  public enum ZabbixWorkspaceModes {
    PVC("PVC"),
    EPH("EPHEMERAL");

    private String mode;

    ZabbixWorkspaceModes(String mode) {
      this.mode = mode;
    }

    public String get() {
      return this.mode;
    }
  }

  public enum ZabbixWorkspaceClusterNames {
    PROD_1A("PROD_1A"),
    PROD_1B("PROD_1B"),
    PROD_2("PROD_2"),
    PROD_2A("PROD_2A"),
    PREVIEW_2A("PREVIEW_2A");

    private String name;

    ZabbixWorkspaceClusterNames(String name) {
      this.name = name;
    }

    public String get() {
      return this.name;
    }
  }

  public enum ZabbixWorkspaceActions {
    START("START"),
    STOP("STOP");

    private String action;

    ZabbixWorkspaceActions(String action) {
      this.action = action;
    }

    public String get() {
      return this.action;
    }
  }

  public enum ZabbixWorkspaceItemIDs {
    EPHEMERAL_PROD_1A_START("EPHEMERAL_PROD_1A_START", "1367263"),
    EPHEMERAL_PROD_1B_START("EPHEMERAL_PROD_1B_START", "1367264"),
    EPHEMERAL_PROD_2_START("EPHEMERAL_PROD_2_START", "1367262"),
    EPHEMERAL_PROD_2A_START("EPHEMERAL_PROD_2A_START", "1367261"),
    EPHEMERAL_PREVIEW_2A_START("EPHEMERAL_PREVIEW_2A_START", "1369751"),

    EPHEMERAL_PROD_1A_STOP("EPHEMERAL_PROD_1A_STOP", "1367273"),
    EPHEMERAL_PROD_1B_STOP("EPHEMERAL_PROD_1B_STOP", "1367274"),
    EPHEMERAL_PROD_2_STOP("EPHEMERAL_PROD_2_STOP", "1367272"),
    EPHEMERAL_PROD_2A_STOP("EPHEMERAL_PROD_2A_STOP", "1367271"),
    EPHEMERAL_PREVIEW_2A_STOP("EPHEMERAL_PREVIEW_2A_STOP", "1369753"),

    PVC_PROD_1A_START("PVC_PROD_1A_START", "1362108"),
    PVC_PROD_1B_START("PVC_PROD_1B_START", "1362174"),
    PVC_PROD_2_START("PVC_PROD_2_START", "1060894"),
    PVC_PROD_2A_START("PVC_PROD_2A_START", "1060893"),
    PVC_PREVIEW_2A_START("PVC_PREVIEW_2A_START", "1369750"),

    PVC_PROD_1A_STOP("PVC_PROD_1A_STOP", "1362114"),
    PVC_PROD_1B_STOP("PVC_PROD_1B_STOP", "1362180"),
    PVC_PROD_2_STOP("PVC_PROD_2_STOP", "1060914"),
    PVC_PROD_2A_STOP("PVC_PROD_2A_STOP", "1060913"),
    PVC_PREVIEW_2A_STOP("PVC_PREVIEW_2A_STOP", "1369752");

    private String id;
    private String name;

    ZabbixWorkspaceItemIDs(String name, String id) {
      this.id = id;
      this.name = name;
    }

    public String getIdForItem(String name) {
      AtomicReference<String> id = new AtomicReference<>(null);
      Arrays.stream(ZabbixWorkspaceItemIDs.values())
          .forEach(
              item -> {
                if (Objects.equals(item.name, name)) id.set(item.id);
              });
      return id.get();
    }

    public static String getNameForItem(String id) {
      AtomicReference<String> name = new AtomicReference<>(null);
      Arrays.stream(ZabbixWorkspaceItemIDs.values())
          .forEach(
              item -> {
                if (Objects.equals(item.id, id)) name.set(item.name);
              });
      return name.get();
    }

    public static Set<String> getItemIDs() {
      Set<String> itemIDs = new HashSet<>();
      Arrays.stream(ZabbixWorkspaceItemIDs.values()).forEach(item -> itemIDs.add(item.id));
      return itemIDs;
    }
  }
}
