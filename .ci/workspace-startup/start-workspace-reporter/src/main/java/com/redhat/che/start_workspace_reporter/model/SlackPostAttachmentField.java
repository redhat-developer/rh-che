package com.redhat.che.start_workspace_reporter.model;

import com.google.gson.annotations.SerializedName;

public class SlackPostAttachmentField {

  private String title;
  private String value;

  @SerializedName("short")
  private boolean shortBoolean;

  public SlackPostAttachmentField() {
    this.title = null;
    this.value = null;
    this.shortBoolean = false;
  }

  public SlackPostAttachmentField(String title, String value, boolean shortBoolean) {
    this.title = title;
    this.value = value;
    this.shortBoolean = shortBoolean;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public boolean isShortBoolean() {
    return shortBoolean;
  }

  public void setShortBoolean(boolean shortBoolean) {
    this.shortBoolean = shortBoolean;
  }

  @Override
  public String toString() {
    return "{\"title\":"
        + this.title
        + ",\"value\":"
        + this.value
        + ",\"short\":"
        + shortBoolean
        + "}";
  }
}
