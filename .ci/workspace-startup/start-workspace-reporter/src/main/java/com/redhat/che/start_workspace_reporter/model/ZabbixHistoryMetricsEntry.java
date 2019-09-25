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
package com.redhat.che.start_workspace_reporter.model;

@SuppressWarnings("WeakerAccess")
public class ZabbixHistoryMetricsEntry implements Comparable<ZabbixHistoryMetricsEntry> {

  private String itemid;
  private String clock;
  private String value;
  private String ns;

  public ZabbixHistoryMetricsEntry(String itemid, String clock, String value, String ns) {
    this.itemid = itemid;
    this.clock = clock;
    this.value = value;
    this.ns = ns;
  }

  public String getItemid() {
    return itemid;
  }

  public void setItemid(String itemid) {
    this.itemid = itemid;
  }

  public String getClock() {
    return clock;
  }

  public void setClock(String clock) {
    this.clock = clock;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getNs() {
    return ns;
  }

  public void setNs(String ns) {
    this.ns = ns;
  }

  @Override
  public int compareTo(ZabbixHistoryMetricsEntry o) {
    if (Integer.valueOf(o.getClock()) > Integer.valueOf(this.getClock())) return -1;
    if (Integer.valueOf(o.getClock()) < Integer.valueOf(this.getClock())) return 1;
    if (Integer.valueOf(o.getNs()) > Integer.valueOf(this.getNs())) return -1;
    if (Integer.valueOf(o.getNs()) < Integer.valueOf(this.getNs())) return 1;
    return 0;
  }
}
