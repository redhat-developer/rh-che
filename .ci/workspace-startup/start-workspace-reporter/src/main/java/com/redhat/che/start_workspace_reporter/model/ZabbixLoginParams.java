package com.redhat.che.start_workspace_reporter.model;

public class ZabbixLoginParams {

  private String user;
  private String password;

  public ZabbixLoginParams() {}

  public ZabbixLoginParams(String user, String password) {
    this.user = user;
    this.password = password;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
