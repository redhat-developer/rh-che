package com.redhat.che.start_workspace_reporter.model;

import java.util.List;

public class SlackPostAttachment {

  private String fallback;
  private String pretext;
  private String color;
  private List<String> mrkdwn_in;
  private List<SlackPostAttachmentField> fields;

  public SlackPostAttachment() {
    this.fallback = "This is a default fallback message";
    this.pretext = null;
    this.color = null;
    this.mrkdwn_in = null;
    this.fields = null;
  }

  public SlackPostAttachment(
      String fallback,
      String pretext,
      String color,
      List<String> mrkdwn_in,
      List<SlackPostAttachmentField> fields) {
    this.fallback = fallback;
    this.pretext = pretext;
    this.color = color;
    this.mrkdwn_in = mrkdwn_in;
    this.fields = fields;
  }

  public String getFallback() {
    return fallback;
  }

  public void setFallback(String fallback) {
    this.fallback = fallback;
  }

  public String getPretext() {
    return pretext;
  }

  public void setPretext(String pretext) {
    this.pretext = pretext;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public List<String> getMrkdwn_in() {
    return mrkdwn_in;
  }

  public void setMrkdwn_in(List<String> mrkdwn_in) {
    this.mrkdwn_in = mrkdwn_in;
  }

  public List<SlackPostAttachmentField> getFields() {
    return fields;
  }

  public void setFields(List<SlackPostAttachmentField> fields) {
    this.fields = fields;
  }

  @Override
  public String toString() {
    String fields = "";
    if (this.fields != null) {
      StringBuilder builder = new StringBuilder();
      builder.append("[");
      if (this.fields.size() > 0) {
        for (SlackPostAttachmentField f : this.fields) {
          builder.append(f.toString());
          builder.append(",");
        }
        builder.deleteCharAt(builder.length() - 1);
      }
      builder.append("]");
      fields = builder.toString();
    }
    String mrkdwn = "";
    if (this.mrkdwn_in != null) {
      StringBuilder builder = new StringBuilder();
      builder.append("[");
      if (this.mrkdwn_in.size() > 0) {
        for (String s : this.mrkdwn_in) {
          builder.append("\"").append(s).append("\",");
        }
        builder.deleteCharAt(builder.length() - 1);
      }
      builder.append("]");
      mrkdwn = builder.toString();
    }
    return "\"fallback\":"
        + this.fallback
        + ",pretext:"
        + this.pretext
        + ",\"color\":"
        + this.color
        + ",\"mrkdwn_in\":"
        + mrkdwn
        + ",\"fields\":"
        + fields
        + "}";
  }
}
