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

import static com.redhat.che.start_workspace_reporter.util.Constants.*;
import static com.redhat.che.start_workspace_reporter.util.Constants.SUFFIX_AVG;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZabbixWorkspaceActions.START;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZabbixWorkspaceActions.STOP;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZabbixWorkspaceModes.EPH;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZabbixWorkspaceModes.PVC;

import com.google.gson.Gson;
import com.redhat.che.start_workspace_reporter.ReporterMain;
import com.redhat.che.start_workspace_reporter.model.HttpRequestWrapperResponse;
import com.redhat.che.start_workspace_reporter.model.SlackPost;
import com.redhat.che.start_workspace_reporter.model.SlackPostAttachment;
import com.redhat.che.start_workspace_reporter.model.SlackPostAttachmentField;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;

public class SlackHelper {

  private static final Logger LOG = Logger.getLogger(SlackHelper.class.getName());
  private static final Gson gson = new Gson();

  private static final HttpRequestWrapper slackRequestWrapper =
      new HttpRequestWrapper(System.getenv("SLACK_URL"));

  public static SlackPost prepareSlackPost(
      Map<String, Float> zabbixMaxAvgValuesHistory, Map<String, Float> zabbixMaxAvgValues) {
    InputStream slackPostIS =
        ReporterMain.class.getClassLoader().getResourceAsStream("slack_post_template.json");
    assert slackPostIS != null;
    InputStreamReader slackPostISReader = new InputStreamReader(slackPostIS);
    SlackPost slackPost = gson.fromJson(slackPostISReader, SlackPost.class);
    List<SlackPostAttachment> attachments = slackPost.getAttachments();
    List<SlackPostAttachment> newAttachments = new ArrayList<>();
    for (SlackPostAttachment a : attachments) {
      String attachmentColor = a.getColor();
      // If it's a field that needs to have it's values set
      if (attachmentColor != null) {
        Constants.ZabbixWorkspaceClusterNames clusterName;
        switch (attachmentColor) {
          case "STARTER_US_EAST_1A_COLOR":
            clusterName = Constants.ZabbixWorkspaceClusterNames.PROD_1A;
            break;
          case "STARTER_US_EAST_1B_COLOR":
            clusterName = Constants.ZabbixWorkspaceClusterNames.PROD_1B;
            break;
          case "STARTER_US_EAST_2_COLOR":
            clusterName = Constants.ZabbixWorkspaceClusterNames.PROD_2;
            break;
          case "STARTER_US_EAST_2A_COLOR":
            clusterName = Constants.ZabbixWorkspaceClusterNames.PROD_2A;
            break;
          case "STARTER_US_EAST_2A_PREVIEW_COLOR":
            clusterName = Constants.ZabbixWorkspaceClusterNames.PREVIEW_2A;
            break;
          default:
            clusterName = null;
            break;
        }
        prepareSlackAttachment(zabbixMaxAvgValues, zabbixMaxAvgValuesHistory, a, clusterName);
      }
      newAttachments.add(a);
    }
    slackPost.setAttachments(newAttachments);
    String channel = System.getenv("SLACK_CHANNEL");
    slackPost.setChannel(channel != null ? channel : "#devtools-che");
    LOG.info(gson.toJson(slackPost));
    return slackPost;
  }

  public static void sendSlackMessage(SlackPost slackPost) {
    HttpRequestWrapperResponse response = null;
    try {
      HttpResponse tmp =
          slackRequestWrapper.post(
              "", ContentType.APPLICATION_JSON.toString(), gson.toJson(slackPost));
      response = new HttpRequestWrapperResponse(tmp);
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to contact slack bot:" + e.getLocalizedMessage());
    } catch (IllegalArgumentException e) {
      LOG.log(Level.SEVERE, "Wrapper failed to parse HtppResponse:" + e.getLocalizedMessage());
    }
    if (response != null) {
      if (responseSuccess(response)) {
        LOG.log(Level.INFO, "Slack message sent successfully.");
      } else {
        try {
          LOG.log(Level.SEVERE, "Failed to send slack message:" + response.grabContent());
        } catch (IOException e) {
          LOG.log(Level.SEVERE, "Failed to parse slack error response:" + e.getLocalizedMessage());
        }
      }
    }
  }

  private static void prepareSlackAttachment(
      Map<String, Float> zabbixMaxAvgValues,
      Map<String, Float> zabbixMaxAvgValuesHistory,
      SlackPostAttachment a,
      Constants.ZabbixWorkspaceClusterNames clusterName) {
    Map<String, Float> clusterChangesMap =
        calculateChangeAndSetColor(clusterName, zabbixMaxAvgValues, zabbixMaxAvgValuesHistory, a);
    createAndSetFields(
        clusterName, zabbixMaxAvgValues, zabbixMaxAvgValuesHistory, a, clusterChangesMap);
  }

  private static void createAndSetFields(
      Constants.ZabbixWorkspaceClusterNames clusterName,
      Map<String, Float> zabbixMaxAvgValues,
      Map<String, Float> zabbixMaxAvgValuesHistory,
      SlackPostAttachment a,
      Map<String, Float> cluster_changes) {
    List<SlackPostAttachmentField> cluster_fields = new ArrayList<>();
    String startMaxKey = "_" + clusterName.get() + "_" + START.get() + SUFFIX_MAX;
    String startAvgKey = "_" + clusterName.get() + "_" + START.get() + SUFFIX_AVG;
    String stopMaxKey = "_" + clusterName.get() + "_" + STOP.get() + SUFFIX_MAX;
    String stopAvgKey = "_" + clusterName.get() + "_" + STOP.get() + SUFFIX_AVG;
    String startTimesString =
        generateAttachmentString(
            zabbixMaxAvgValues,
            zabbixMaxAvgValuesHistory,
            cluster_changes,
            startAvgKey,
            startMaxKey,
            "pvc_start_avg",
            "eph_start_avg");
    SlackPostAttachmentField cluster_start_times =
        new SlackPostAttachmentField(
            "Cluster  " + clusterName.get() + " start", startTimesString, true);
    String stopTimesString =
        generateAttachmentString(
            zabbixMaxAvgValues,
            zabbixMaxAvgValuesHistory,
            cluster_changes,
            stopAvgKey,
            stopMaxKey,
            "pvc_stop_avg",
            "eph_stop_avg");
    SlackPostAttachmentField cluster_stop_times =
        new SlackPostAttachmentField(
            "Cluster  " + clusterName.get() + " stop", stopTimesString, true);
    cluster_fields.add(cluster_start_times);
    cluster_fields.add(cluster_stop_times);
    a.setFields(cluster_fields);
  }

  private static String generateAttachmentString(
      Map<String, Float> zabbixMaxAvgValues,
      Map<String, Float> zabbixMaxAvgValuesHistory,
      Map<String, Float> cluster_changes,
      String actionAvgKey,
      String actionMaxKey,
      String pvcActionAvgChangeKey,
      String ephActionAvgChangeKey) {
    Float pvcAvgSeconds = zabbixMaxAvgValues.get(PVC.get().concat(actionAvgKey)) / 1000;
    Float pvcMaxSeconds = zabbixMaxAvgValues.get(PVC.get().concat(actionMaxKey)) / 1000;
    Float pvcAvgDiffPercentage = cluster_changes.get(pvcActionAvgChangeKey);
    Float pvcAvgDiffSeconds =
        zabbixMaxAvgValues.get(PVC.get().concat(actionAvgKey)) / 1000
            - zabbixMaxAvgValuesHistory.get(PVC.get().concat(actionAvgKey)) / 1000;
    Float ephAvgSeconds = zabbixMaxAvgValues.get(EPH.get().concat(actionAvgKey)) / 1000;
    Float ephMaxSeconds = zabbixMaxAvgValues.get(EPH.get().concat(actionMaxKey)) / 1000;
    Float ephAvgDiffPercentage = cluster_changes.get(ephActionAvgChangeKey);
    Float ephAvgDiffSeconds =
        zabbixMaxAvgValues.get(EPH.get().concat(actionAvgKey)) / 1000
            - zabbixMaxAvgValuesHistory.get(EPH.get().concat(actionAvgKey)) / 1000;
    return String.format("*PVC* avg: %.1fs", pvcAvgSeconds)
        .concat(String.format(", max: %.1fs\n", pvcMaxSeconds))
        .concat(
            String.format("avg-diff: %.2f%% {%.2fs}\n", pvcAvgDiffPercentage, pvcAvgDiffSeconds))
        .concat(String.format("*EPH* avg: %.1fs", ephAvgSeconds))
        .concat(String.format(", max: %.1fs\n", ephMaxSeconds))
        .concat(String.format("avg-diff: %.2f%% {%.2fs}", ephAvgDiffPercentage, ephAvgDiffSeconds));
  }

  private static Map<String, Float> calculateChangeAndSetColor(
      Constants.ZabbixWorkspaceClusterNames clusterName,
      Map<String, Float> zabbixMaxAvgValues,
      Map<String, Float> zabbixMaxAvgValuesHistory,
      SlackPostAttachment a) {
    Map<String, Float> changes =
        getChangesMap(clusterName, zabbixMaxAvgValues, zabbixMaxAvgValuesHistory);
    float percentageChange = getMaxChange(changes);
    LOG.info(
        "Average diff percentage:"
            + percentageChange
            + " color:"
            + getColorBasedOnPercentage(percentageChange));
    a.setColor(getColorBasedOnPercentage(percentageChange));
    return changes;
  }

  private static String getColorBasedOnPercentage(float percentage) {
    if (percentage > SLACK_BROKEN_PERCENTAGE) return SLACK_BROKEN_COLOR;
    if (percentage > SLACK_UNSTABLE_PERCENTAGE) return SLACK_UNSTABLE_COLOR;
    return SLACK_SUCCESS_COLOR;
  }

  private static float getMaxChange(Map<String, Float> changes) {
    return Collections.max(changes.values());
  }

  private static Map<String, Float> getChangesMap(
      ZabbixWorkspaceClusterNames clusterName,
      Map<String, Float> zabbixMaxAvgValues,
      Map<String, Float> zabbixMaxAvgValuesHistory) {
    Map<String, Float> changes = new HashMap<>();
    String startAvgKey = "_" + clusterName.get() + "_" + START.get() + SUFFIX_AVG;
    String stopAvgKey = "_" + clusterName.get() + "_" + STOP.get() + SUFFIX_AVG;
    getChangesMapPercentage(
        zabbixMaxAvgValues, zabbixMaxAvgValuesHistory, changes, startAvgKey, "pvc_start_avg", PVC);
    getChangesMapPercentage(
        zabbixMaxAvgValues, zabbixMaxAvgValuesHistory, changes, startAvgKey, "eph_start_avg", EPH);
    getChangesMapPercentage(
        zabbixMaxAvgValues, zabbixMaxAvgValuesHistory, changes, stopAvgKey, "pvc_stop_avg", PVC);
    getChangesMapPercentage(
        zabbixMaxAvgValues, zabbixMaxAvgValuesHistory, changes, stopAvgKey, "eph_stop_avg", EPH);
    return changes;
  }

  private static void getChangesMapPercentage(
      Map<String, Float> zabbixMaxAvgValues,
      Map<String, Float> zabbixMaxAvgValuesHistory,
      Map<String, Float> changes,
      String actionValueKey,
      String changesMapKey,
      Constants.ZabbixWorkspaceModes workspaceMode) {
    Float historyAverageTime = zabbixMaxAvgValuesHistory.get(workspaceMode.get() + actionValueKey);
    Float currentAverageTime = zabbixMaxAvgValues.get(workspaceMode.get() + actionValueKey);
    changes.put(changesMapKey, getPercentageDifference(historyAverageTime, currentAverageTime));
  }

  private static float getPercentageDifference(Float oldValue, Float newValue) {
    return newValue == null || oldValue == null
        ? Float.MAX_VALUE
        : (newValue - oldValue) / oldValue * 100;
  }

  private static boolean responseSuccess(HttpRequestWrapperResponse response) {
    int responseStatusCode = response.getStatusCode();
    return responseStatusCode == 200;
  }
}
