package com.redhat.che.start_workspace_reporter.model;

import java.util.List;

public class SlackPost {

    private String channel;
    private Boolean mrkdwn;
    private Boolean unfurl_links;
    private String text;
    private List<SlackPostAttachment> attachments;

    public SlackPost() {
        this.channel = "#devtools-che";
        this.mrkdwn = null;
        this.unfurl_links = false;
        this.text = "@osioche Screens link:https://zabbix.devshift.net:9443/zabbix/screenconf.php?filter_name=che-perf&filter_set=Filter\nWorkspace startup cluster status:";
        this.attachments = null;
    }

    public SlackPost(String channel, Boolean mrkdown, Boolean unfurl_links, String text, List<SlackPostAttachment> attachments) {
        this.channel = channel;
        this.mrkdwn = mrkdown;
        this.unfurl_links = unfurl_links;
        this.text = text;
        this.attachments = attachments;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
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
