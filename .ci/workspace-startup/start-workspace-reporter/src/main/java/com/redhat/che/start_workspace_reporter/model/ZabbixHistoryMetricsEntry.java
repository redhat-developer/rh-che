package com.redhat.che.start_workspace_reporter.model;

public class ZabbixHistoryMetricsEntry {

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
}
