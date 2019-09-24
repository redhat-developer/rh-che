package com.redhat.che.start_workspace_reporter.util;

import static com.redhat.che.start_workspace_reporter.util.Constants.SUFFIX_AVG;
import static com.redhat.che.start_workspace_reporter.util.Constants.SUFFIX_MAX;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.redhat.che.start_workspace_reporter.ReporterMain;
import com.redhat.che.start_workspace_reporter.model.HttpRequestWrapperResponse;
import com.redhat.che.start_workspace_reporter.model.JSONRPCRequest;
import com.redhat.che.start_workspace_reporter.model.ZabbixHistoryMetricsEntry;
import com.redhat.che.start_workspace_reporter.model.ZabbixLoginParams;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;

public class ZabbixHelper {

  private static final Logger LOG = Logger.getLogger(ZabbixHelper.class.getName());
  private static final Gson gson = new Gson();
  private static final JsonParser parser = new JsonParser();

  private static final HttpRequestWrapper zabbixRequestWrapper =
      new HttpRequestWrapper(System.getenv("ZABBIX_URL"));

  public static HttpRequestWrapperResponse getZabbixHeartbeat() {
    HttpRequestWrapperResponse response = null;
    InputStream versionRequestIS =
        ReporterMain.class.getClassLoader().getResourceAsStream("version_request.json");
    assert versionRequestIS != null;
    InputStreamReader versionRequestISReader = new InputStreamReader(versionRequestIS);
    JSONRPCRequest versionRequest = gson.fromJson(versionRequestISReader, JSONRPCRequest.class);
    try {
      HttpResponse tmp =
          zabbixRequestWrapper.post(
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

  public static void calculateZabbixResults(
      List<ZabbixHistoryMetricsEntry> zabbixHistoryResults, Map<String, Float> zabbixMaxAvgValues) {
    zabbixHistoryResults
        .parallelStream()
        .forEach(
            result -> {
              String itemName = Constants.ZabbixWorkspaceItemIDs.getNameForItem(result.getItemid());
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

  public static HttpRequestWrapperResponse logInToZabbix() {
    HttpRequestWrapperResponse response = null;
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
          zabbixRequestWrapper.post(
              "/api_jsonrpc.php", ContentType.APPLICATION_JSON.toString(), loginRequest.toString());
      response = new HttpRequestWrapperResponse(tmp);
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed to contact zabbix on devshift.net:" + e.getLocalizedMessage());
    } catch (IllegalArgumentException e) {
      LOG.log(Level.SEVERE, "Wrapper failed to parse HtppResponse:" + e.getLocalizedMessage());
    }
    return response;
  }

  public static String extractZabbixToken(HttpRequestWrapperResponse response) {
    if (response != null) {
      if (responseSuccess(response)) {
        LOG.log(Level.INFO, "Zabbix login successful.");
        try {
          return response.asJSONRPCResponse().getResult().getAsString();
        } catch (IOException e) {
          LOG.log(
              Level.SEVERE, "Failed to get login response auth token:" + e.getLocalizedMessage());
          return null;
        }
      } else {
        return null;
      }
    }
    return null;
  }

  public static boolean grabHistoryDataFromZabbix(
      JSONRPCRequest getHistoryRequest, List<ZabbixHistoryMetricsEntry> zabbixHistoryResults) {
    HttpRequestWrapperResponse response = null;
    try {
      HttpResponse tmp =
          zabbixRequestWrapper.post(
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
          return true;
        }
      } else {
        LOG.log(Level.SEVERE, "Zabbix getHistory request failed.");
        return true;
      }
    }
    return false;
  }

  private static boolean responseSuccess(HttpRequestWrapperResponse response) {
    int responseStatusCode = response.getStatusCode();
    return responseStatusCode == 200;
  }
}
