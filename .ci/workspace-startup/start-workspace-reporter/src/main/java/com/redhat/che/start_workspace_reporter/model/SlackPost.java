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

import java.util.List;

public class SlackPost {

  private String channel;
  private String icon_emoji;
  private Boolean mrkdwn;
  private Boolean unfurl_links;
  private String text;
  private List<SlackPostAttachment> attachments;

  private static final String DEFAULT_REPORT_CHANNEL = "#devtools-che";
  private static final String DEFAULT_REPORT_EMOJI = ":chart_with_upwards_trend:";
  private static final String DEFAULT_REPORT_TEXT =
      "@osioche Screens link:https://zabbix.devshift.net:9443/zabbix/screenconf.php?filter_name=che-perf&filter_set=Filter\nWorkspace startup cluster status:";

  public SlackPost() {
    setChannel(DEFAULT_REPORT_CHANNEL);
    setIcon_emoji(DEFAULT_REPORT_EMOJI);
    setMrkdwn(null);
    setUnfurl_links(false);
    setText(DEFAULT_REPORT_TEXT);
    setAttachments(null);
  }

  public SlackPost(
      String channel,
      Boolean mrkdown,
      Boolean unfurl_links,
      String text,
      List<SlackPostAttachment> attachments) {
    setChannel(channel);
    setIcon_emoji(DEFAULT_REPORT_EMOJI);
    setMrkdwn(mrkdown);
    setUnfurl_links(unfurl_links);
    setText(text);
    setAttachments(attachments);
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getIcon_emoji() {
    return this.icon_emoji;
  }

  public void setIcon_emoji(String icon_emoji) {
    this.icon_emoji = icon_emoji;
  }

  public Boolean getUnfurl_links() {
    return unfurl_links;
  }

  public void setUnfurl_links(Boolean unfurl_links) {
    this.unfurl_links = unfurl_links;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public Boolean getMrkdwn() {
    return mrkdwn;
  }

  public void setMrkdwn(Boolean mrkdwn) {
    this.mrkdwn = mrkdwn;
  }

  public List<SlackPostAttachment> getAttachments() {
    return attachments;
  }

  public void setAttachments(List<SlackPostAttachment> attachments) {
    this.attachments = attachments;
  }
}
