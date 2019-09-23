package com.redhat.che.start_workspace_reporter;

import static com.redhat.che.start_workspace_reporter.util.Constants.TIMESTAMP_LAST_SEVEN_DAYS;
import static com.redhat.che.start_workspace_reporter.util.Constants.TIMESTAMP_NOW;
import static com.redhat.che.start_workspace_reporter.util.Constants.TIMESTAMP_YESTERDAY;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZABBIX_HISTORY_GET_ALL_ENTRIES;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZabbixWorkspaceActions.START;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZabbixWorkspaceActions.STOP;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZabbixWorkspaceClusterNames;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZabbixWorkspaceItemIDs;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZabbixWorkspaceModes.EPHEMERAL;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZabbixWorkspaceModes.PVC;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.redhat.che.start_workspace_reporter.model.*;
import com.redhat.che.start_workspace_reporter.util.HttpRequestWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;

public class ReporterMain {

  private static final Logger LOG = Logger.getLogger(ReporterMain.class.getName());
  private static final Gson gson = new Gson();
  private static final JsonParser parser = new JsonParser();
  private static final AtomicInteger pvc_cycles_count = new AtomicInteger(0);
  private static final AtomicInteger eph_cycles_count = new AtomicInteger(0);

  private static final String SLACK_SUCCESS_COLOR = "#2EB886";
  private static final String SLACK_UNSTABLE_COLOR = "#FFAA00";
  private static final String SLACK_BROKEN_COLOR = "#FF0000";
  private static final float SLACK_BROKEN_PERCENTAGE = 5f; // above value
  private static final float SLACK_UNSTABLE_PERCENTAGE = 1f; // above value
  private static final String SUFFIX_AVG = "_AVG";
  private static final String SUFFIX_MAX = "_MAX";
  private static final String SUFFIX_PREVIOUS = "_PREVIOUS";

  public static void main(String[] args) {
    HttpRequestWrapper zabbixWrapper = new HttpRequestWrapper(System.getenv("ZABBIX_URL"));
    HttpRequestWrapper slackWrapper = new HttpRequestWrapper(System.getenv("SLACK_URL"));
    HttpRequestWrapperResponse response = null;
    InputStream versionRequestIS =
        ReporterMain.class.getClassLoader().getResourceAsStream("version_request.json");
    InputStream loginRequestIS =
        ReporterMain.class.getClassLoader().getResourceAsStream("login_request.json");
    InputStream getHistoryRequestIS =
        ReporterMain.class.getClassLoader().getResourceAsStream("get_history_request.json");
    InputStream slackPostIS =
        ReporterMain.class.getClassLoader().getResourceAsStream("slack_post_template.json");
    assert versionRequestIS != null;
    InputStreamReader versionRequestISReader = new InputStreamReader(versionRequestIS);
    assert loginRequestIS != null;
    InputStreamReader loginRequestISReader = new InputStreamReader(loginRequestIS);
    assert getHistoryRequestIS != null;
    InputStreamReader getHistoryRequestISReader = new InputStreamReader(getHistoryRequestIS);
    assert slackPostIS != null;
    InputStreamReader slackPostISReader = new InputStreamReader(slackPostIS);
    ZabbixLoginParams loginParams =
        new ZabbixLoginParams(System.getenv("ZABBIX_USERNAME"), System.getenv("ZABBIX_PASSWORD"));
    JSONRPCRequest versionRequest = gson.fromJson(versionRequestISReader, JSONRPCRequest.class);
    JSONRPCRequest loginRequest = gson.fromJson(loginRequestISReader, JSONRPCRequest.class);
    SlackPost slackPost = gson.fromJson(slackPostISReader, SlackPost.class);

    try {
      HttpResponse tmp =
          zabbixWrapper.post(
              "/api_jsonrpc.php",
              ContentType.APPLICATION_JSON.toString(),
              versionRequest.toString());
      response = new HttpRequestWrapperResponse(tmp);
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to contact zabbix on devshift.net:" + e.getLocalizedMessage());
    } catch (IllegalArgumentException e) {
      LOG.log(Level.SEVERE, "Wrapper failed to parse HtppResponse:" + e.getLocalizedMessage());
    }
    if (response != null) {
      if (responseSuccess(response)) {
        LOG.log(Level.INFO, "Zabbix heartbeat successful.");
      } else {
        return;
      }
    }

    String zabbixAuthToken = null;
    loginRequest.setParams(parser.parse(gson.toJson(loginParams)));
    try {
      HttpResponse tmp =
          zabbixWrapper.post(
              "/api_jsonrpc.php", ContentType.APPLICATION_JSON.toString(), loginRequest.toString());
      response = new HttpRequestWrapperResponse(tmp);
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to contact zabbix on devshift.net:" + e.getLocalizedMessage());
    } catch (IllegalArgumentException e) {
      LOG.log(Level.SEVERE, "Wrapper failed to parse HtppResponse:" + e.getLocalizedMessage());
    }
    if (response != null) {
      if (responseSuccess(response)) {
        LOG.log(Level.INFO, "Zabbix login successful.");
        try {
          zabbixAuthToken = response.asJSONRPCResponse().getResult().getAsString();
        } catch (IOException e) {
          LOG.log(
              Level.SEVERE, "Failed to get login response auth token:" + e.getLocalizedMessage());
          return;
        }
      } else {
        return;
      }
    }

    JSONRPCRequest getHistoryRequest =
        gson.fromJson(getHistoryRequestISReader, JSONRPCRequest.class);
    getHistoryRequest.setAuth(zabbixAuthToken);
    ZabbixHistoryParams historyParams = new ZabbixHistoryParams();

    historyParams.setItemids(ZabbixWorkspaceItemIDs.getItemIDs());
    historyParams.setLimit(ZABBIX_HISTORY_GET_ALL_ENTRIES);
    historyParams.setOutput((Set<String>) null);
    historyParams.setSortfield(ZabbixHistoryParams.SortField.CLOCK.toString());
    historyParams.setSortorder(ZabbixHistoryParams.SortOrder.ASC.toString());

    List<ZabbixHistoryMetricsEntry> zabbixHistoryResults = new ArrayList<>();
    Map<String, Float> zabbixMaxAvgValuesHistory = new HashMap<>();
    Map<String, Float> zabbixMaxAvgValues = new HashMap<>();

    pvc_cycles_count.set(0);
    eph_cycles_count.set(0);
    historyParams.setTime_from(TIMESTAMP_LAST_SEVEN_DAYS);
    historyParams.setTime_till(TIMESTAMP_YESTERDAY);
    getHistoryRequest.setParams(parser.parse(gson.toJson(historyParams)));

    if (!grabHistoryDataFromZabbix(
        zabbixWrapper, response, getHistoryRequest, zabbixHistoryResults)) return;

    calculateZabbixResults(zabbixHistoryResults, zabbixMaxAvgValuesHistory);

    zabbixHistoryResults = new ArrayList<>();
    pvc_cycles_count.set(0);
    eph_cycles_count.set(0);
    historyParams.setTime_from(TIMESTAMP_YESTERDAY);
    historyParams.setTime_till(TIMESTAMP_NOW);
    getHistoryRequest.setParams(parser.parse(gson.toJson(historyParams)));

    if (!grabHistoryDataFromZabbix(
        zabbixWrapper, response, getHistoryRequest, zabbixHistoryResults)) return;

    calculateZabbixResults(zabbixHistoryResults, zabbixMaxAvgValues);

    List<SlackPostAttachment> attachments = slackPost.getAttachments();
    List<SlackPostAttachment> newAttachments = new ArrayList<>();
    for (SlackPostAttachment a : attachments) {
      String attachmentColor = a.getColor();
      // If it's a field that needs to have it's values set
      if (attachmentColor != null) {
        switch (attachmentColor) {
          case "STARTER_US_EAST_1A_COLOR":
            Map<String, Float> starter_1a_changes =
                calculateChangeAndSetColor(
                    ZabbixWorkspaceClusterNames.PROD_1A,
                    zabbixMaxAvgValues,
                    zabbixMaxAvgValuesHistory,
                    a);
            createAndSetFields(
                ZabbixWorkspaceClusterNames.PROD_1A, zabbixMaxAvgValues, a, starter_1a_changes);
            break;
          case "STARTER_US_EAST_1B_COLOR":
            Map<String, Float> starter_1b_changes =
                calculateChangeAndSetColor(
                    ZabbixWorkspaceClusterNames.PROD_1B,
                    zabbixMaxAvgValues,
                    zabbixMaxAvgValuesHistory,
                    a);
            createAndSetFields(
                ZabbixWorkspaceClusterNames.PROD_1B, zabbixMaxAvgValues, a, starter_1b_changes);
            break;
          case "STARTER_US_EAST_2_COLOR":
            Map<String, Float> starter_2_changes =
                calculateChangeAndSetColor(
                    ZabbixWorkspaceClusterNames.PROD_2,
                    zabbixMaxAvgValues,
                    zabbixMaxAvgValuesHistory,
                    a);
            createAndSetFields(
                ZabbixWorkspaceClusterNames.PROD_2, zabbixMaxAvgValues, a, starter_2_changes);
            break;
          case "STARTER_US_EAST_2A_COLOR":
            Map<String, Float> starter_2a_changes =
                calculateChangeAndSetColor(
                    ZabbixWorkspaceClusterNames.PROD_2A,
                    zabbixMaxAvgValues,
                    zabbixMaxAvgValuesHistory,
                    a);
            createAndSetFields(
                ZabbixWorkspaceClusterNames.PROD_2A, zabbixMaxAvgValues, a, starter_2a_changes);
            break;
          case "STARTER_US_EAST_2A_PREVIEW_COLOR":
            Map<String, Float> starter_2a_preview_changes =
                calculateChangeAndSetColor(
                    ZabbixWorkspaceClusterNames.PREVIEW_2A,
                    zabbixMaxAvgValues,
                    zabbixMaxAvgValuesHistory,
                    a);
            createAndSetFields(
                ZabbixWorkspaceClusterNames.PREVIEW_2A,
                zabbixMaxAvgValues,
                a,
                starter_2a_preview_changes);
            break;
          default:
            break;
        }
      }
      newAttachments.add(a);
    }
    slackPost.setAttachments(newAttachments);
    String channel = System.getenv("SLACK_CHANNEL");
    slackPost.setChannel(channel != null ? channel : "#devtools-che");
    LOG.info(gson.toJson(slackPost));

    try {
      HttpResponse tmp =
          slackWrapper.post("", ContentType.APPLICATION_JSON.toString(), gson.toJson(slackPost));
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

  private static void createAndSetFields(
      ZabbixWorkspaceClusterNames clusterName,
      Map<String, Float> zabbixMaxAvgValues,
      SlackPostAttachment a,
      Map<String, Float> cluster_changes) {
    List<SlackPostAttachmentField> cluster_fields = new ArrayList<>();
    String startMaxKey = "_" + clusterName.get() + "_" + START.get() + SUFFIX_MAX;
    String startAvgKey = "_" + clusterName.get() + "_" + START.get() + SUFFIX_AVG;
    String stopMaxKey = "_" + clusterName.get() + "_" + STOP.get() + SUFFIX_MAX;
    String stopAvgKey = "_" + clusterName.get() + "_" + STOP.get() + SUFFIX_AVG;
    String startTimesString =
        String.format(
                "*PVC* avg: %.1fs", zabbixMaxAvgValues.get(PVC.get().concat(startAvgKey)) / 1000)
            .concat(
                String.format(
                    ", max: %.1fs\n", zabbixMaxAvgValues.get(PVC.get().concat(startMaxKey)) / 1000))
            .concat(String.format("avg-diff: %.2f%%\n", cluster_changes.get("pvc_start_avg")))
            .concat(
                String.format(
                    "*EPH* avg: %.1fs",
                    zabbixMaxAvgValues.get(EPHEMERAL.get().concat(startAvgKey)) / 1000))
            .concat(
                String.format(
                    ", max: %.1fs\n",
                    zabbixMaxAvgValues.get(EPHEMERAL.get().concat(startMaxKey)) / 1000))
            .concat(String.format("avg-diff: %.2f%%", cluster_changes.get("eph_start_avg")));
    SlackPostAttachmentField cluster_start_times =
        new SlackPostAttachmentField(
            "Cluster  " + clusterName.get() + " start", startTimesString, true);
    String stopTimesString =
        String.format(
                "*PVC* avg: %.1fs", zabbixMaxAvgValues.get(PVC.get().concat(stopAvgKey)) / 1000)
            .concat(
                String.format(
                    ", max: %.1fs\n", zabbixMaxAvgValues.get(PVC.get().concat(stopMaxKey)) / 1000))
            .concat(String.format("avg-diff: %.2f%%\n", cluster_changes.get("pvc_stop_avg")))
            .concat(
                String.format(
                    "*EPH* avg: %.1fs",
                    zabbixMaxAvgValues.get(EPHEMERAL.get().concat(startAvgKey)) / 1000))
            .concat(
                String.format(
                    ", max: %.1fs\n",
                    zabbixMaxAvgValues.get(EPHEMERAL.get().concat(startMaxKey)) / 1000))
            .concat(String.format("avg-diff: %.2f%%", cluster_changes.get("eph_stop_avg")));
    SlackPostAttachmentField cluster_stop_times =
        new SlackPostAttachmentField(
            "Cluster  " + clusterName.get() + " stop", stopTimesString, true);
    cluster_fields.add(cluster_start_times);
    cluster_fields.add(cluster_stop_times);
    a.setFields(cluster_fields);
  }

  private static Map<String, Float> calculateChangeAndSetColor(
      ZabbixWorkspaceClusterNames clusterName,
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
    return Math.max(
        changes.get("pvc_start_avg"),
        Math.max(
            changes.get("eph_start_avg"),
            Math.max(changes.get("pvc_stop_avg"), changes.get("eph_stop_avg"))));
  }

  private static Map<String, Float> getChangesMap(
      ZabbixWorkspaceClusterNames clusterName,
      Map<String, Float> zabbixMaxAvgValues,
      Map<String, Float> zabbixMaxAvgValuesHistory) {
    Map<String, Float> changes = new HashMap<>();
    String startMaxKey = "_" + clusterName.get() + "_" + START.get() + SUFFIX_MAX;
    String startAvgKey = "_" + clusterName.get() + "_" + START.get() + SUFFIX_AVG;
    String stopMaxKey = "_" + clusterName.get() + "_" + STOP.get() + SUFFIX_MAX;
    String stopAvgKey = "_" + clusterName.get() + "_" + STOP.get() + SUFFIX_AVG;
    changes.put(
        "pvc_start_avg",
        getPercentageDifference(
            zabbixMaxAvgValuesHistory.get(PVC.get() + startAvgKey),
            zabbixMaxAvgValues.get(PVC.get() + startAvgKey)));
    changes.put(
        "pvc_start_max",
        getPercentageDifference(
            zabbixMaxAvgValuesHistory.get(PVC.get() + startMaxKey),
            zabbixMaxAvgValues.get(PVC.get() + startMaxKey)));
    changes.put(
        "eph_start_avg",
        getPercentageDifference(
            zabbixMaxAvgValuesHistory.get(EPHEMERAL.get() + startAvgKey),
            zabbixMaxAvgValues.get(EPHEMERAL.get() + startAvgKey)));
    changes.put(
        "eph_start_max",
        getPercentageDifference(
            zabbixMaxAvgValuesHistory.get(EPHEMERAL.get() + startMaxKey),
            zabbixMaxAvgValues.get(EPHEMERAL.get() + startMaxKey)));
    changes.put(
        "pvc_stop_avg",
        getPercentageDifference(
            zabbixMaxAvgValuesHistory.get(PVC.get() + stopAvgKey),
            zabbixMaxAvgValues.get(PVC.get() + stopAvgKey)));
    changes.put(
        "pvc_stop_max",
        getPercentageDifference(
            zabbixMaxAvgValuesHistory.get(PVC.get() + stopMaxKey),
            zabbixMaxAvgValues.get(PVC.get() + stopMaxKey)));
    changes.put(
        "eph_stop_avg",
        getPercentageDifference(
            zabbixMaxAvgValuesHistory.get(EPHEMERAL.get() + stopAvgKey),
            zabbixMaxAvgValues.get(EPHEMERAL.get() + stopAvgKey)));
    changes.put(
        "eph_stop_max",
        getPercentageDifference(
            zabbixMaxAvgValuesHistory.get(EPHEMERAL.get() + stopMaxKey),
            zabbixMaxAvgValues.get(EPHEMERAL.get() + stopMaxKey)));
    return changes;
  }

  private static float getPercentageDifference(Float oldValue, Float newValue) {
    return newValue == null || oldValue == null
        ? Float.MAX_VALUE
        : (newValue - oldValue) / oldValue * 100;
  }

  private static boolean grabHistoryDataFromZabbix(
      HttpRequestWrapper wrapper,
      HttpRequestWrapperResponse response,
      JSONRPCRequest getHistoryRequest,
      List<ZabbixHistoryMetricsEntry> zabbixHistoryResults) {
    try {
      HttpResponse tmp =
          wrapper.post(
              "/api_jsonrpc.php",
              ContentType.APPLICATION_JSON.toString(),
              getHistoryRequest.toString());
      response = new HttpRequestWrapperResponse(tmp);
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to contact zabbix on devshift.net:" + e.getLocalizedMessage());
    } catch (IllegalArgumentException e) {
      LOG.log(Level.SEVERE, "Wrapper failed to parse HtppResponse:" + e.getLocalizedMessage());
    }
    if (response != null) {
      if (responseSuccess(response)) {
        LOG.log(Level.INFO, "Zabbix getHistory request successful.");
        try {
          JsonArray tmp = gson.fromJson(response.asJSONRPCResponse().getResult(), JsonArray.class);
          tmp.forEach(
              e -> zabbixHistoryResults.add(gson.fromJson(e, ZabbixHistoryMetricsEntry.class)));
        } catch (IOException e) {
          LOG.log(
              Level.SEVERE, "Failed to parse response into value list:" + e.getLocalizedMessage());
          return false;
        }
      } else {
        LOG.log(Level.SEVERE, "Zabbix getHistory request failed.");
        return false;
      }
    }
    return true;
  }

  private static void calculateZabbixResults(
      List<ZabbixHistoryMetricsEntry> zabbixHistoryResults, Map<String, Float> zabbixMaxAvgValues) {
    zabbixHistoryResults
        .parallelStream()
        .forEach(
            result -> {
              String itemName = ZabbixWorkspaceItemIDs.getNameForItem(result.getItemid());
              Float itemCurrentValue = Float.valueOf(result.getValue());
              Float itemMaxValue = zabbixMaxAvgValues.get(itemName.concat(SUFFIX_MAX));
              Float itemAvgValue = zabbixMaxAvgValues.get(itemName.concat(SUFFIX_AVG));
              if (itemMaxValue != null) {
                itemMaxValue = itemMaxValue < itemCurrentValue ? itemCurrentValue : itemMaxValue;
              } else {
                itemMaxValue = itemCurrentValue;
              }
              if (itemAvgValue != null) {
                itemAvgValue = (itemAvgValue + itemCurrentValue) / 2f;
              } else {
                itemAvgValue = itemCurrentValue;
              }
              zabbixMaxAvgValues.put(itemName.concat(SUFFIX_MAX), itemMaxValue);
              zabbixMaxAvgValues.put(itemName.concat(SUFFIX_AVG), itemAvgValue);
            });
  }

  private static boolean responseSuccess(HttpRequestWrapperResponse response) {
    int responseStatusCode = response.getStatusCode();
    return responseStatusCode == 200;
  }
}
