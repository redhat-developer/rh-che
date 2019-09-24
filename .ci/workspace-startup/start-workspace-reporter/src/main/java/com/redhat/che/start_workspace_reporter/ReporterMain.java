package com.redhat.che.start_workspace_reporter;

import static com.redhat.che.start_workspace_reporter.util.Constants.*;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.redhat.che.start_workspace_reporter.model.*;
import com.redhat.che.start_workspace_reporter.util.SlackHelper;
import com.redhat.che.start_workspace_reporter.util.ZabbixHelper;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Logger;

public class ReporterMain {

  private static final Logger LOG = Logger.getLogger(ReporterMain.class.getName());
  private static final Gson gson = new Gson();
  private static final JsonParser parser = new JsonParser();

  public static void main(String[] args) {
    InputStream getHistoryRequestIS =
        ReporterMain.class.getClassLoader().getResourceAsStream("get_history_request.json");
    assert getHistoryRequestIS != null;
    InputStreamReader getHistoryRequestISReader = new InputStreamReader(getHistoryRequestIS);

    /* =================== *
     *  Contacting zabbix  *
     * =================== */
    HttpRequestWrapperResponse response;

    // Get zabbix heartbeat
    response = ZabbixHelper.getZabbixHeartbeat();
    if (response == null) {
      throw new RuntimeException("Zabbix heartbeat has failed, response is null.");
    }

    // Log in to zabbix and get auth token
    response = ZabbixHelper.logInToZabbix();
    String zabbixAuthToken = ZabbixHelper.extractZabbixToken(response);

    /* ======================== *
     *  Getting and processing  *
     *     data from Zabbix     *
     * ======================== */

    Map<String, Float> zabbixMaxAvgValuesHistory = new HashMap<>();
    Map<String, Float> zabbixMaxAvgValues = new HashMap<>();

    JSONRPCRequest getHistoryRequest =
        gson.fromJson(getHistoryRequestISReader, JSONRPCRequest.class);

    ZabbixHistoryParams historyParams = new ZabbixHistoryParams();
    historyParams.setItemids(ZabbixWorkspaceItemIDs.getItemIDs());
    historyParams.setLimit(ZABBIX_HISTORY_GET_ALL_ENTRIES);
    historyParams.setOutput((Set<String>) null);
    historyParams.setSortfield(ZabbixHistoryParams.SortField.CLOCK.toString());
    historyParams.setSortorder(ZabbixHistoryParams.SortOrder.ASC.toString());

    // Get last seven days history data
    List<ZabbixHistoryMetricsEntry> zabbixHistoryResults = new ArrayList<>();
    historyParams.setTime_from(TIMESTAMP_LAST_SEVEN_DAYS);
    historyParams.setTime_till(TIMESTAMP_YESTERDAY);
    getHistoryRequest.setParams(parser.parse(gson.toJson(historyParams)));
    getHistoryRequest.setAuth(zabbixAuthToken);

    if (ZabbixHelper.grabHistoryDataFromZabbix(getHistoryRequest, zabbixHistoryResults)) return;

    ZabbixHelper.calculateZabbixResults(zabbixHistoryResults, zabbixMaxAvgValuesHistory);

    // Get today's data
    zabbixHistoryResults = new ArrayList<>();
    historyParams.setTime_from(TIMESTAMP_YESTERDAY);
    historyParams.setTime_till(TIMESTAMP_NOW);
    getHistoryRequest.setParams(parser.parse(gson.toJson(historyParams)));

    if (ZabbixHelper.grabHistoryDataFromZabbix(getHistoryRequest, zabbixHistoryResults)) return;

    ZabbixHelper.calculateZabbixResults(zabbixHistoryResults, zabbixMaxAvgValues);

    SlackPost slackPost =
        SlackHelper.prepareSlackPost(zabbixMaxAvgValuesHistory, zabbixMaxAvgValues);

    SlackHelper.sendSlackMessage(slackPost);
  }
}
