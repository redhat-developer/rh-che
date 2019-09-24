package com.redhat.che.start_workspace_reporter;

import static com.redhat.che.start_workspace_reporter.util.Constants.TIMESTAMP_LAST_SEVEN_DAYS;
import static com.redhat.che.start_workspace_reporter.util.Constants.TIMESTAMP_NOW;
import static com.redhat.che.start_workspace_reporter.util.Constants.TIMESTAMP_YESTERDAY;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZABBIX_HISTORY_GET_ALL_ENTRIES;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZabbixWorkspaceActions.START;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZabbixWorkspaceActions.STOP;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZabbixWorkspaceClusterNames;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZabbixWorkspaceItemIDs;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZabbixWorkspaceModes.EPH;
import static com.redhat.che.start_workspace_reporter.util.Constants.ZabbixWorkspaceModes.PVC;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.redhat.che.start_workspace_reporter.model.*;
import com.redhat.che.start_workspace_reporter.util.Constants;
import com.redhat.che.start_workspace_reporter.util.HttpRequestWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
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

  private static final HttpRequestWrapper zabbixWrapper =
      new HttpRequestWrapper(System.getenv("ZABBIX_URL"));
  private static final HttpRequestWrapper slackWrapper =
      new HttpRequestWrapper(System.getenv("SLACK_URL"));
  private static String zabbixAuthToken = null;

  public static void main(String[] args) {
    InputStream getHistoryRequestIS =
        ReporterMain.class.getClassLoader().getResourceAsStream("get_history_request.json");
    InputStream slackPostIS =
        ReporterMain.class.getClassLoader().getResourceAsStream("slack_post_template.json");
    assert getHistoryRequestIS != null;
    InputStreamReader getHistoryRequestISReader = new InputStreamReader(getHistoryRequestIS);
    assert slackPostIS != null;
    InputStreamReader slackPostISReader = new InputStreamReader(slackPostIS);
    SlackPost slackPost = gson.fromJson(slackPostISReader, SlackPost.class);

    /* =================== *
     *  Contacting zabbix  *
     * =================== */
    HttpRequestWrapperResponse response;

    // Get zabbix heartbeat
    response = getZabbixHeartbeat();
    if (response == null) {
      throw new RuntimeException("Zabbix heartbeat has failed, response is null.");
    }

    // Log in to zabbix and get auth token
    InputStream loginRequestIS =
        ReporterMain.class.getClassLoader().getResourceAsStream("login_request.json");
    assert loginRequestIS != null;
    InputStreamReader loginRequestISReader = new InputStreamReader(loginRequestIS);
    JSONRPCRequest loginRequest = gson.fromJson(loginRequestISReader, JSONRPCRequest.class);
    ZabbixLoginParams loginParams =
        new ZabbixLoginParams(System.getenv("ZABBIX_USERNAME"), System.getenv("ZABBIX_PASSWORD"));
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

    historyParams.setTime_from(TIMESTAMP_LAST_SEVEN_DAYS);
    historyParams.setTime_till(TIMESTAMP_YESTERDAY);
    getHistoryRequest.setParams(parser.parse(gson.toJson(historyParams)));

    if (!grabHistoryDataFromZabbix(
        zabbixWrapper, response, getHistoryRequest, zabbixHistoryResults)) return;

    calculateZabbixResults(zabbixHistoryResults, zabbixMaxAvgValuesHistory);

    zabbixHistoryResults = new ArrayList<>();
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
            prepareSlackAttachment(
                zabbixMaxAvgValuesHistory,
                zabbixMaxAvgValues,
                a,
                ZabbixWorkspaceClusterNames.PROD_1A);
            break;
          case "STARTER_US_EAST_1B_COLOR":
            prepareSlackAttachment(
                zabbixMaxAvgValuesHistory,
                zabbixMaxAvgValues,
                a,
                ZabbixWorkspaceClusterNames.PROD_1B);
            break;
          case "STARTER_US_EAST_2_COLOR":
            prepareSlackAttachment(
                zabbixMaxAvgValuesHistory,
                zabbixMaxAvgValues,
                a,
                ZabbixWorkspaceClusterNames.PROD_2);
            break;
          case "STARTER_US_EAST_2A_COLOR":
            prepareSlackAttachment(
                zabbixMaxAvgValuesHistory,
                zabbixMaxAvgValues,
                a,
                ZabbixWorkspaceClusterNames.PROD_2A);
            break;
          case "STARTER_US_EAST_2A_PREVIEW_COLOR":
            prepareSlackAttachment(
                zabbixMaxAvgValuesHistory,
                zabbixMaxAvgValues,
                a,
                ZabbixWorkspaceClusterNames.PREVIEW_2A);
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

  private static void prepareSlackAttachment(
      Map<String, Float> zabbixMaxAvgValuesHistory,
      Map<String, Float> zabbixMaxAvgValues,
      SlackPostAttachment a,
      ZabbixWorkspaceClusterNames clusterName) {
    Map<String, Float> clusterChangesMap =
        calculateChangeAndSetColor(clusterName, zabbixMaxAvgValues, zabbixMaxAvgValuesHistory, a);
    createAndSetFields(clusterName, zabbixMaxAvgValues, a, clusterChangesMap);
  }

  private static void storeAverageHistory(
      Map<String, Float> zabbixMaxAvgValuesHistory, Map<String, Float> zabbixMaxAvgValues) {
    zabbixMaxAvgValuesHistory
        .entrySet()
        .parallelStream()
        .forEach(
            entry -> {
              if (entry.getKey().contains(SUFFIX_AVG))
                zabbixMaxAvgValues.put(entry.getKey(), entry.getValue());
            });
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
        generateAttachmentString(
            zabbixMaxAvgValues,
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
      Map<String, Float> cluster_changes,
      String actionAvgKey,
      String actionMaxKey,
      String pvcActionAvgChangeKey,
      String ephActionAvgChangeKey) {
    return String.format(
            "*PVC* avg: %.1fs", zabbixMaxAvgValues.get(PVC.get().concat(actionAvgKey)) / 1000)
        .concat(
            String.format(
                ", max: %.1fs\n", zabbixMaxAvgValues.get(PVC.get().concat(actionMaxKey)) / 1000))
        .concat(String.format("avg-diff: %.2f%%\n", cluster_changes.get(pvcActionAvgChangeKey)))
        .concat(
            String.format(
                "*EPH* avg: %.1fs", zabbixMaxAvgValues.get(EPH.get().concat(actionAvgKey)) / 1000))
        .concat(
            String.format(
                ", max: %.1fs\n", zabbixMaxAvgValues.get(EPH.get().concat(actionMaxKey)) / 1000))
        .concat(String.format("avg-diff: %.2f%%", cluster_changes.get(ephActionAvgChangeKey)));
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

  private static HttpRequestWrapperResponse getZabbixHeartbeat() {
    HttpRequestWrapperResponse response = null;
    InputStream versionRequestIS =
        ReporterMain.class.getClassLoader().getResourceAsStream("version_request.json");
    assert versionRequestIS != null;
    InputStreamReader versionRequestISReader = new InputStreamReader(versionRequestIS);
    JSONRPCRequest versionRequest = gson.fromJson(versionRequestISReader, JSONRPCRequest.class);
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
        return null;
      }
    }
    return response;
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
