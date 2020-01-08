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
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;

public class SlackHelper {

  private static final Logger LOG = Logger.getLogger(SlackHelper.class.getName());
  private static final Gson gson = new Gson();

  private static final Float DEFAULT_WORKSPACE_STARTUP_WARNING_THRESHOLD = 35f;
  private static final Float DEFAULT_WORKSPACE_STARTUP_FAILURE_THRESHOLD = 60f;
  private static final Float DEFAULT_WORKSPACE_STOP_WARNING_THRESHOLD = 5f;
  private static final Float DEFAULT_WORKSPACE_STOP_FAILURE_THRESHOLD = 10f;

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
    AtomicReference<Float> startWarningThreshold = new AtomicReference<>();
    AtomicReference<Float> startFailureThreshold = new AtomicReference<>();
    AtomicReference<Float> stopWarningThreshold = new AtomicReference<>();
    AtomicReference<Float> stopFailureThreshold = new AtomicReference<>();
    setFailureThresholdValues(
        startWarningThreshold, startFailureThreshold, stopWarningThreshold, stopFailureThreshold);
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
        prepareSlackAttachment(
            zabbixMaxAvgValues,
            zabbixMaxAvgValuesHistory,
            a,
            clusterName,
            startWarningThreshold.get(),
            startFailureThreshold.get(),
            stopWarningThreshold.get(),
            stopFailureThreshold.get());
      }
      newAttachments.add(a);
    }
    slackPost.setAttachments(newAttachments);
    String channel = System.getenv("SLACK_CHANNEL");
    slackPost.setChannel(channel != null ? channel : "#devtools-che");
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
      Constants.ZabbixWorkspaceClusterNames clusterName,
      Float startWarningThreshold,
      Float startFailureThreshold,
      Float stopWarningThreshold,
      Float stopFailureThreshold) {
    Map<String, Float> clusterChangesMap =
        calculateChangeAndSetColor(
            clusterName,
            zabbixMaxAvgValues,
            zabbixMaxAvgValuesHistory,
            a,
            startWarningThreshold,
            startFailureThreshold,
            stopWarningThreshold,
            stopFailureThreshold);
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
    // Create strings for PVC part of the attachment
    String pvcAvgString = "*PVC* avg: NO_DATA";
    if (zabbixMaxAvgValues.containsKey(PVC.get().concat(actionAvgKey)))
      pvcAvgString =
          String.format(
              "*PVC* avg: %.1fs", zabbixMaxAvgValues.get(PVC.get().concat(actionAvgKey)) / 1000);
    String pvcMaxString = ", max: NO_DATA\n";
    if (zabbixMaxAvgValues.containsKey(PVC.get().concat(actionMaxKey)))
      pvcMaxString =
          String.format(
              ", max: %.1fs\n", zabbixMaxAvgValues.get(PVC.get().concat(actionMaxKey)) / 1000);
    String pvcAvgDiff = "NO_DATA ";
    String pvcAvgDiffSeconds = "{NO_HISTORY}\n";
    if (cluster_changes.containsKey(pvcActionAvgChangeKey))
      pvcAvgDiff = String.format("%.2f%% ", cluster_changes.get(pvcActionAvgChangeKey));
    if (zabbixMaxAvgValuesHistory.containsKey(PVC.get().concat(actionAvgKey))) {
      pvcAvgDiffSeconds = "{CANNOT_CALCULATE}\n";
      if (zabbixMaxAvgValues.containsKey(PVC.get().concat(actionAvgKey)))
        pvcAvgDiffSeconds =
            String.format(
                "{%.2fs}\n",
                zabbixMaxAvgValues.get(PVC.get().concat(actionAvgKey)) / 1000
                    - zabbixMaxAvgValuesHistory.get(PVC.get().concat(actionAvgKey)) / 1000);
    }
    String pvcAvgDiffString = "avg-diff: ".concat(pvcAvgDiff).concat(pvcAvgDiffSeconds);

    // Create strings for EPH part of the attachments
    String ephAvgString = "*EPH* avg: NO_DATA";
    if (zabbixMaxAvgValues.containsKey(EPH.get().concat(actionAvgKey)))
      ephAvgString =
          String.format(
              "*EPH* avg: %.1fs", zabbixMaxAvgValues.get(EPH.get().concat(actionAvgKey)) / 1000);
    String ephMaxString = ", max: NO_DATA\n";
    if (zabbixMaxAvgValues.containsKey(EPH.get().concat(actionMaxKey)))
      ephMaxString =
          String.format(
              ", max: %.1fs\n", zabbixMaxAvgValues.get(EPH.get().concat(actionMaxKey)) / 1000);
    String ephAvgDiff = "NO_DATA ";
    String ephAvgDiffSeconds = "{NO_HISTORY}\n";
    if (cluster_changes.containsKey(ephActionAvgChangeKey))
      ephAvgDiff = String.format("%.2f%% ", cluster_changes.get(ephActionAvgChangeKey));
    if (zabbixMaxAvgValuesHistory.containsKey(EPH.get().concat(actionAvgKey))) {
      ephAvgDiffSeconds = "{CANNOT_CALCULATE}";
      if (zabbixMaxAvgValues.containsKey(EPH.get().concat(actionAvgKey)))
        ephAvgDiffSeconds =
            String.format(
                "{%.2fs}",
                zabbixMaxAvgValues.get(EPH.get().concat(actionAvgKey)) / 1000
                    - zabbixMaxAvgValuesHistory.get(EPH.get().concat(actionAvgKey)) / 1000);
    }
    String ephAvgDiffString = "avg-diff: ".concat(ephAvgDiff).concat(ephAvgDiffSeconds);

    return pvcAvgString
        .concat(pvcMaxString)
        .concat(pvcAvgDiffString)
        .concat(ephAvgString)
        .concat(ephMaxString)
        .concat(ephAvgDiffString);
  }

  private static Map<String, Float> calculateChangeAndSetColor(
      Constants.ZabbixWorkspaceClusterNames clusterName,
      Map<String, Float> zabbixMaxAvgValues,
      Map<String, Float> zabbixMaxAvgValuesHistory,
      SlackPostAttachment a,
      Float startWarningThreshold,
      Float startFailureThreshold,
      Float stopWarningThreshold,
      Float stopFailureThreshold) {
    Map<String, Float> changes =
        getChangesMap(clusterName, zabbixMaxAvgValues, zabbixMaxAvgValuesHistory);
    if (zabbixMaxAvgValues.containsKey(
            PVC.get() + "_" + clusterName + "_" + START.get() + SUFFIX_AVG)
        && zabbixMaxAvgValues.containsKey(
            EPH.get() + "_" + clusterName + "_" + START.get() + SUFFIX_AVG)
        && zabbixMaxAvgValues.containsKey(
            PVC.get() + "_" + clusterName + "_" + STOP.get() + SUFFIX_AVG)
        && zabbixMaxAvgValues.containsKey(
            EPH.get() + "_" + clusterName + "_" + STOP.get() + SUFFIX_AVG)) {
      String attachmentColor =
          setAttachmentColorBasedOnThresholds(
              zabbixMaxAvgValues,
              clusterName.get(),
              startWarningThreshold,
              startFailureThreshold,
              stopWarningThreshold,
              stopFailureThreshold);
      a.setColor(attachmentColor);
    } else {
      LOG.info("No data available - color set to SLACK_NO_DATA_COLOR.");
      a.setColor(SLACK_NO_DATA_COLOR);
    }
    return changes;
  }

  private static String setAttachmentColorBasedOnThresholds(
      Map<String, Float> zabbixMaxAvgValues,
      String clusterName,
      Float startWarningThreshold,
      Float startFailureThreshold,
      Float stopWarningThreshold,
      Float stopFailureThreshold) {
    float pvcStartAvg;
    float ephStartAvg;
    float pvcStopAvg;
    float ephStopAvg;
    pvcStartAvg =
        zabbixMaxAvgValues.get(PVC.get() + "_" + clusterName + "_" + START.get() + SUFFIX_AVG)
            / 1000f;
    ephStartAvg =
        zabbixMaxAvgValues.get(EPH.get() + "_" + clusterName + "_" + START.get() + SUFFIX_AVG)
            / 1000f;
    pvcStopAvg =
        zabbixMaxAvgValues.get(PVC.get() + "_" + clusterName + "_" + STOP.get() + SUFFIX_AVG)
            / 1000f;
    ephStopAvg =
        zabbixMaxAvgValues.get(EPH.get() + "_" + clusterName + "_" + STOP.get() + SUFFIX_AVG)
            / 1000f;
    if (pvcStartAvg < startWarningThreshold
        && ephStartAvg < startWarningThreshold
        && pvcStopAvg < stopWarningThreshold
        && ephStopAvg < stopWarningThreshold) return SLACK_SUCCESS_COLOR;
    if (pvcStartAvg < startFailureThreshold
        && ephStartAvg < startFailureThreshold
        && pvcStopAvg < stopFailureThreshold
        && ephStopAvg < stopFailureThreshold) return SLACK_UNSTABLE_COLOR;
    return SLACK_BROKEN_COLOR;
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
    if (zabbixMaxAvgValues.containsKey(workspaceMode.get() + actionValueKey)
        && zabbixMaxAvgValuesHistory.containsKey(workspaceMode.get() + actionValueKey)) {
      Float historyAverageTime =
          zabbixMaxAvgValuesHistory.get(workspaceMode.get() + actionValueKey);
      Float currentAverageTime = zabbixMaxAvgValues.get(workspaceMode.get() + actionValueKey);
      changes.put(changesMapKey, getPercentageDifference(historyAverageTime, currentAverageTime));
    }
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

  private static void setFailureThresholdValues(
      AtomicReference<Float> startWarningThreshold,
      AtomicReference<Float> startFailureThreshold,
      AtomicReference<Float> stopWarningThreshold,
      AtomicReference<Float> stopFailureThreshold) {
    startWarningThreshold.set(DEFAULT_WORKSPACE_STARTUP_WARNING_THRESHOLD);
    try {
      startWarningThreshold.set(
          Float.parseFloat(System.getenv("WORKSPACE_STARTUP_WARNING_THRESHOLD")));
    } catch (Exception e) {
      LOG.warning("Could not parse workspace startup warning threshold, using default value");
    }
    startFailureThreshold.set(DEFAULT_WORKSPACE_STARTUP_FAILURE_THRESHOLD);
    try {
      startFailureThreshold.set(
          Float.parseFloat(System.getenv("WORKSPACE_STARTUP_FAILURE_THRESHOLD")));
    } catch (Exception e) {
      LOG.warning("Could not parse workspace startup failure threshold, using default value");
    }
    stopWarningThreshold.set(DEFAULT_WORKSPACE_STOP_WARNING_THRESHOLD);
    try {
      stopWarningThreshold.set(Float.parseFloat(System.getenv("WORKSPACE_STOP_WARNING_THRESHOLD")));
    } catch (Exception e) {
      LOG.warning("Could not parse workspace stop warning threshold, using default value");
    }
    stopFailureThreshold.set(DEFAULT_WORKSPACE_STOP_FAILURE_THRESHOLD);
    try {
      stopFailureThreshold.set(Float.parseFloat(System.getenv("WORKSPACE_STOP_FAILURE_THRESHOLD")));
    } catch (Exception e) {
      LOG.warning("Could not parse workspace stop failure threshold, using default value");
    }
  }
}
