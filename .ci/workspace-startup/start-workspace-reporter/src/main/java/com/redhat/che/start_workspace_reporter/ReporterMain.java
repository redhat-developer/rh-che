package com.redhat.che.start_workspace_reporter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.redhat.che.start_workspace_reporter.model.*;
import com.redhat.che.start_workspace_reporter.util.HttpRequestWrapper;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReporterMain {

    private static final Logger LOG = Logger.getLogger(ReporterMain.class.getName());
    private static final Gson gson = new Gson();
    private static final JsonParser parser = new JsonParser();
    private static final AtomicInteger pvc_cycles_count = new AtomicInteger(0);
    private static final AtomicInteger eph_cycles_count = new AtomicInteger(0);
    private static final Long ONE_DAY_MILLIS = 86_400_000L;
    private static final Long SEVEN_DAYS_LILLIS = 604_800_000L;
    private static final Long TIMESTAMP_NOW = (System.currentTimeMillis())/1000;
    private static final Long TIMESTAMP_YESTERDAY = (System.currentTimeMillis() - ONE_DAY_MILLIS)/1000;
    private static final Long TIMESTAMP_LAST_SEVEN_DAYS = (System.currentTimeMillis() - ONE_DAY_MILLIS - SEVEN_DAYS_LILLIS)/1000;
    private static final int ZABBIX_HISTORY_GET_ALL_ENTRIES = 0;

    private static final String ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_1a = "1367263";
    private static final String ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_1b = "1367264";
    private static final String ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_2 = "1367262";
    private static final String ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_2a = "1367261";
    private static final String ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_PREVIEW_2a = "1369751";

    private static final String ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_1a = "1367273";
    private static final String ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_1b = "1367274";
    private static final String ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_2 = "1367272";
    private static final String ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_2a = "1367271";
    private static final String ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_PREVIEW_2a = "1369753";

    private static final String ZABBIX_WORKSPACE_START_TIME_ID_PROD_1a = "1362108";
    private static final String ZABBIX_WORKSPACE_START_TIME_ID_PROD_1b = "1362174";
    private static final String ZABBIX_WORKSPACE_START_TIME_ID_PROD_2 = "1060894";
    private static final String ZABBIX_WORKSPACE_START_TIME_ID_PROD_2a = "1060893";
    private static final String ZABBIX_WORKSPACE_START_TIME_ID_PROD_PREVIEW_2a = "1369750";

    private static final String ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_1a = "1362114";
    private static final String ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_1b = "1362180";
    private static final String ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_2 = "1060914";
    private static final String ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_2a = "1060913";
    private static final String ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_PREVIEW_2a = "1369752";
    private static final float MINMAX_INIT_VALUE = -1f;

    private static final String SLACK_SUCCESS_COLOR = "#2EB886";
    private static final String SLACK_UNSTABLE_COLOR = "#FFAA00";
    private static final String SLACK_BROKEN_COLOR = "#FF0000";
    private static final float SLACK_BROKEN_PERCENTAGE = 5f; // above value
    private static final float SLACK_UNSTABLE_PERCENTAGE = 1f; // above value

    public static void main(String[] args) {
        HttpRequestWrapper zabbixWrapper = new HttpRequestWrapper(System.getenv("ZABBIX_URL"));
        HttpRequestWrapper slackWrapper = new HttpRequestWrapper(System.getenv("SLACK_URL"));
        HttpRequestWrapperResponse response = null;
        InputStream versionRequestIS = ReporterMain.class.getClassLoader().getResourceAsStream("version_request.json");
        InputStream loginRequestIS = ReporterMain.class.getClassLoader().getResourceAsStream("login_request.json");
        InputStream getHistoryRequestIS = ReporterMain.class.getClassLoader().getResourceAsStream("get_history_request.json");
        InputStream slackPostIS = ReporterMain.class.getClassLoader().getResourceAsStream("slack_post_template.json");
        assert versionRequestIS != null;
        InputStreamReader versionRequestISReader = new InputStreamReader(versionRequestIS);
        assert loginRequestIS != null;
        InputStreamReader loginRequestISReader = new InputStreamReader(loginRequestIS);
        assert getHistoryRequestIS != null;
        InputStreamReader getHistoryRequestISReader = new InputStreamReader(getHistoryRequestIS);
        assert slackPostIS != null;
        InputStreamReader slackPostISReader = new InputStreamReader(slackPostIS);
        ZabbixLoginParams loginParams = new ZabbixLoginParams(System.getenv("ZABBIX_USERNAME"), System.getenv("ZABBIX_PASSWORD"));
        JSONRPCRequest versionRequest = gson.fromJson(versionRequestISReader, JSONRPCRequest.class);
        JSONRPCRequest loginRequest = gson.fromJson(loginRequestISReader, JSONRPCRequest.class);
        SlackPost slackPost = gson.fromJson(slackPostISReader, SlackPost.class);

        try {
            HttpResponse tmp = zabbixWrapper.post("/api_jsonrpc.php", ContentType.APPLICATION_JSON.toString(), versionRequest.toString());
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
            HttpResponse tmp = zabbixWrapper.post("/api_jsonrpc.php", ContentType.APPLICATION_JSON.toString(), loginRequest.toString());
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
                    LOG.log(Level.SEVERE, "Failed to get login response auth token:"+e.getLocalizedMessage());
                    return;
                }
            } else {
                return;
            }
        }

        JSONRPCRequest getHistoryRequest = gson.fromJson(getHistoryRequestISReader, JSONRPCRequest.class);
        getHistoryRequest.setAuth(zabbixAuthToken);
        ZabbixHistoryParams historyParams = new ZabbixHistoryParams();
        Set<String> zabbixHistoryParamsItemIDs = new HashSet<>();

        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_1a);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_1b);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_2);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_2a);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_PREVIEW_2a);

        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_1a);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_1b);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_2);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_2a);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_PREVIEW_2a);

        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_ID_PROD_1a);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_ID_PROD_1b);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_ID_PROD_2);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_ID_PROD_2a);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_START_TIME_ID_PROD_PREVIEW_2a);

        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_1a);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_1b);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_2);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_2a);
        zabbixHistoryParamsItemIDs.add(ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_PREVIEW_2a);

        historyParams.setItemids(zabbixHistoryParamsItemIDs);
        historyParams.setLimit(ZABBIX_HISTORY_GET_ALL_ENTRIES);
        historyParams.setOutput((Set<String>)null);
        historyParams.setSortfield(ZabbixHistoryParams.SortField.CLOCK.toString());
        historyParams.setSortorder(ZabbixHistoryParams.SortOrder.ASC.toString());

        List<ZabbixHistoryMetricsEntry> zabbixHistoryResults = new ArrayList<>();

        AtomicReference<Float> yesterday_zabbix_starter_us_east_1a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1b_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_preview_2a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1b_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_preview_2a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1b_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_preview_2a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_1b_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_2a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_preview_2a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1b_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_preview_2a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1b_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_preview_2a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1b_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_preview_2a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_1b_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_2a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> yesterday_zabbix_starter_us_east_eph_preview_2a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);

        AtomicReference<Float> zabbix_starter_us_east_1a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1b_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_preview_2a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1b_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_preview_2a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1b_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_preview_2a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_1b_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_2a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_preview_2a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1b_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_start_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1b_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_start_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1b_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_stop_max = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_1b_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_2a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);
        AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_stop_avg = new AtomicReference<>(MINMAX_INIT_VALUE);

        pvc_cycles_count.set(0);
        eph_cycles_count.set(0);
        historyParams.setTime_from(TIMESTAMP_LAST_SEVEN_DAYS);
        historyParams.setTime_till(TIMESTAMP_YESTERDAY);
        getHistoryRequest.setParams(parser.parse(gson.toJson(historyParams)));

        if (!grabHistoryDataFromZabbix(zabbixWrapper, response, getHistoryRequest, zabbixHistoryResults)) return;

        calculateZabbixResults(zabbixHistoryResults,
                zabbix_starter_us_east_1a_start_max, zabbix_starter_us_east_1b_start_max, zabbix_starter_us_east_2_start_max, zabbix_starter_us_east_2a_start_max, zabbix_starter_us_east_preview_2a_start_max,
                zabbix_starter_us_east_1a_start_avg, zabbix_starter_us_east_1b_start_avg, zabbix_starter_us_east_2_start_avg, zabbix_starter_us_east_2a_start_avg, zabbix_starter_us_east_preview_2a_start_avg,
                zabbix_starter_us_east_1a_stop_max, zabbix_starter_us_east_1b_stop_max, zabbix_starter_us_east_2_stop_max, zabbix_starter_us_east_2a_stop_max, zabbix_starter_us_east_preview_2a_stop_max,
                zabbix_starter_us_east_1a_stop_avg, zabbix_starter_us_east_1b_stop_avg, zabbix_starter_us_east_2_stop_avg, zabbix_starter_us_east_2a_stop_avg, zabbix_starter_us_east_preview_2a_stop_avg,
                zabbix_starter_us_east_eph_1a_start_max, zabbix_starter_us_east_eph_1b_start_max, zabbix_starter_us_east_eph_2_start_max, zabbix_starter_us_east_eph_2a_start_max, zabbix_starter_us_east_eph_preview_2a_start_max,
                zabbix_starter_us_east_eph_1a_start_avg, zabbix_starter_us_east_eph_1b_start_avg, zabbix_starter_us_east_eph_2_start_avg, zabbix_starter_us_east_eph_2a_start_avg, zabbix_starter_us_east_eph_preview_2a_start_avg,
                zabbix_starter_us_east_eph_1a_stop_max, zabbix_starter_us_east_eph_1b_stop_max, zabbix_starter_us_east_eph_2_stop_max, zabbix_starter_us_east_eph_2a_stop_max, zabbix_starter_us_east_eph_preview_2a_stop_max,
                zabbix_starter_us_east_eph_1a_stop_avg, zabbix_starter_us_east_eph_1b_stop_avg, zabbix_starter_us_east_eph_2_stop_avg, zabbix_starter_us_east_eph_2a_stop_avg, zabbix_starter_us_east_eph_preview_2a_stop_avg);

        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1a_start_max, zabbix_starter_us_east_1a_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1a_start_avg, zabbix_starter_us_east_1a_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1a_stop_max, zabbix_starter_us_east_1a_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1a_stop_avg, zabbix_starter_us_east_1a_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1a_start_max, zabbix_starter_us_east_eph_1a_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1a_start_avg, zabbix_starter_us_east_eph_1a_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1a_stop_max, zabbix_starter_us_east_eph_1a_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1a_stop_avg, zabbix_starter_us_east_eph_1a_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1b_start_max, zabbix_starter_us_east_1b_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1b_start_avg, zabbix_starter_us_east_1b_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1b_stop_max, zabbix_starter_us_east_1b_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_1b_stop_avg, zabbix_starter_us_east_1b_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1b_start_max, zabbix_starter_us_east_eph_1b_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1b_start_avg, zabbix_starter_us_east_eph_1b_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1b_stop_max, zabbix_starter_us_east_eph_1b_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_1b_stop_avg, zabbix_starter_us_east_eph_1b_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2_start_max, zabbix_starter_us_east_2_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2_start_avg, zabbix_starter_us_east_2_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2_stop_max, zabbix_starter_us_east_2_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2_stop_avg, zabbix_starter_us_east_2_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2_start_max, zabbix_starter_us_east_eph_2_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2_start_avg, zabbix_starter_us_east_eph_2_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2_stop_max, zabbix_starter_us_east_eph_2_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2_stop_avg, zabbix_starter_us_east_eph_2_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2a_start_max, zabbix_starter_us_east_2a_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2a_start_avg, zabbix_starter_us_east_2a_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2a_stop_max, zabbix_starter_us_east_2a_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_2a_stop_avg, zabbix_starter_us_east_2a_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2a_start_max, zabbix_starter_us_east_eph_2a_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2a_start_avg, zabbix_starter_us_east_eph_2a_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2a_stop_max, zabbix_starter_us_east_eph_2a_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_2a_stop_avg, zabbix_starter_us_east_eph_2a_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_preview_2a_start_max, zabbix_starter_us_east_preview_2a_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_preview_2a_start_avg, zabbix_starter_us_east_preview_2a_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_preview_2a_stop_max, zabbix_starter_us_east_preview_2a_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_preview_2a_stop_avg, zabbix_starter_us_east_preview_2a_stop_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_preview_2a_start_max, zabbix_starter_us_east_eph_preview_2a_start_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_preview_2a_start_avg, zabbix_starter_us_east_eph_preview_2a_start_avg);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_preview_2a_stop_max, zabbix_starter_us_east_eph_preview_2a_stop_max);
        storeZabbixValueForComparison(yesterday_zabbix_starter_us_east_eph_preview_2a_stop_avg, zabbix_starter_us_east_eph_preview_2a_stop_avg);

        zabbixHistoryResults = new ArrayList<>();
        pvc_cycles_count.set(0);
        eph_cycles_count.set(0);
        historyParams.setTime_from(TIMESTAMP_YESTERDAY);
        historyParams.setTime_till(TIMESTAMP_NOW);
        getHistoryRequest.setParams(parser.parse(gson.toJson(historyParams)));

        if (!grabHistoryDataFromZabbix(zabbixWrapper, response, getHistoryRequest, zabbixHistoryResults)) return;

        calculateZabbixResults(zabbixHistoryResults,
                zabbix_starter_us_east_1a_start_max, zabbix_starter_us_east_1b_start_max, zabbix_starter_us_east_2_start_max, zabbix_starter_us_east_2a_start_max, zabbix_starter_us_east_preview_2a_start_max,
                zabbix_starter_us_east_1a_start_avg, zabbix_starter_us_east_1b_start_avg, zabbix_starter_us_east_2_start_avg, zabbix_starter_us_east_2a_start_avg, zabbix_starter_us_east_preview_2a_start_avg,
                zabbix_starter_us_east_1a_stop_max, zabbix_starter_us_east_1b_stop_max, zabbix_starter_us_east_2_stop_max, zabbix_starter_us_east_2a_stop_max, zabbix_starter_us_east_preview_2a_stop_max,
                zabbix_starter_us_east_1a_stop_avg, zabbix_starter_us_east_1b_stop_avg, zabbix_starter_us_east_2_stop_avg, zabbix_starter_us_east_2a_stop_avg, zabbix_starter_us_east_preview_2a_stop_avg,
                zabbix_starter_us_east_eph_1a_start_max, zabbix_starter_us_east_eph_1b_start_max, zabbix_starter_us_east_eph_2_start_max, zabbix_starter_us_east_eph_2a_start_max, zabbix_starter_us_east_eph_preview_2a_start_max,
                zabbix_starter_us_east_eph_1a_start_avg, zabbix_starter_us_east_eph_1b_start_avg, zabbix_starter_us_east_eph_2_start_avg, zabbix_starter_us_east_eph_2a_start_avg, zabbix_starter_us_east_eph_preview_2a_start_avg,
                zabbix_starter_us_east_eph_1a_stop_max, zabbix_starter_us_east_eph_1b_stop_max, zabbix_starter_us_east_eph_2_stop_max, zabbix_starter_us_east_eph_2a_stop_max, zabbix_starter_us_east_eph_preview_2a_stop_max,
                zabbix_starter_us_east_eph_1a_stop_avg, zabbix_starter_us_east_eph_1b_stop_avg, zabbix_starter_us_east_eph_2_stop_avg, zabbix_starter_us_east_eph_2a_stop_avg, zabbix_starter_us_east_eph_preview_2a_stop_avg);

        List<SlackPostAttachment> attachments = slackPost.getAttachments();
        List<SlackPostAttachment> newAttachments = new ArrayList<>();
        for (SlackPostAttachment a : attachments) {
            String attachmentColor = a.getColor();
            // If it's a field that needs to have it's values set
            if (attachmentColor != null) {
                switch(attachmentColor) {
                    case "STARTER_US_EAST_1A_COLOR":
                        Map<String, Float> starter_1a_changes = getMaxChangeAndSetColor(yesterday_zabbix_starter_us_east_1a_start_max, yesterday_zabbix_starter_us_east_1a_start_avg,
                                yesterday_zabbix_starter_us_east_eph_1a_start_max, yesterday_zabbix_starter_us_east_eph_1a_start_avg,
                                zabbix_starter_us_east_1a_start_max, zabbix_starter_us_east_1a_start_avg,
                                zabbix_starter_us_east_eph_1a_start_max, zabbix_starter_us_east_eph_1a_start_avg,
                                yesterday_zabbix_starter_us_east_1a_stop_max, yesterday_zabbix_starter_us_east_1a_stop_avg,
                                yesterday_zabbix_starter_us_east_eph_1a_stop_max, yesterday_zabbix_starter_us_east_eph_1a_stop_avg,
                                zabbix_starter_us_east_1a_stop_max, zabbix_starter_us_east_1a_stop_avg,
                                zabbix_starter_us_east_eph_1a_stop_max, zabbix_starter_us_east_eph_1a_stop_avg, a);
                        createAndSetFields(zabbix_starter_us_east_1a_start_max, zabbix_starter_us_east_1a_start_avg,
                                zabbix_starter_us_east_eph_1a_start_max, zabbix_starter_us_east_eph_1a_start_avg,
                                zabbix_starter_us_east_1a_stop_max, zabbix_starter_us_east_1a_stop_avg,
                                zabbix_starter_us_east_eph_1a_stop_max, zabbix_starter_us_east_eph_1a_stop_avg,
                                a, starter_1a_changes, "1a");
                        break;
                    case "STARTER_US_EAST_1B_COLOR":
                        Map<String, Float> starter_1b_changes = getMaxChangeAndSetColor(yesterday_zabbix_starter_us_east_1b_start_max, yesterday_zabbix_starter_us_east_1b_start_avg,
                                yesterday_zabbix_starter_us_east_eph_1b_start_max, yesterday_zabbix_starter_us_east_eph_1b_start_avg,
                                zabbix_starter_us_east_1b_start_max, zabbix_starter_us_east_1b_start_avg,
                                zabbix_starter_us_east_eph_1b_start_max, zabbix_starter_us_east_eph_1b_start_avg,
                                yesterday_zabbix_starter_us_east_1b_stop_max, yesterday_zabbix_starter_us_east_1b_stop_avg,
                                yesterday_zabbix_starter_us_east_eph_1b_stop_max, yesterday_zabbix_starter_us_east_eph_1b_stop_avg,
                                zabbix_starter_us_east_1b_stop_max, zabbix_starter_us_east_1b_stop_avg,
                                zabbix_starter_us_east_eph_1b_stop_max, zabbix_starter_us_east_eph_1b_stop_avg, a);
                        createAndSetFields(zabbix_starter_us_east_1b_start_max, zabbix_starter_us_east_1b_start_avg,
                                zabbix_starter_us_east_eph_1b_start_max, zabbix_starter_us_east_eph_1b_start_avg,
                                zabbix_starter_us_east_1b_stop_max, zabbix_starter_us_east_1b_stop_avg,
                                zabbix_starter_us_east_eph_1b_stop_max, zabbix_starter_us_east_eph_1b_stop_avg,
                                a, starter_1b_changes, "1b");
                        break;
                    case "STARTER_US_EAST_2_COLOR":
                        Map<String, Float> starter_2_changes = getMaxChangeAndSetColor(yesterday_zabbix_starter_us_east_2_start_max, yesterday_zabbix_starter_us_east_2_start_avg,
                                yesterday_zabbix_starter_us_east_eph_2_start_max, yesterday_zabbix_starter_us_east_eph_2_start_avg,
                                zabbix_starter_us_east_2_start_max, zabbix_starter_us_east_2_start_avg,
                                zabbix_starter_us_east_eph_2_start_max, zabbix_starter_us_east_eph_2_start_avg,
                                yesterday_zabbix_starter_us_east_2_stop_max, yesterday_zabbix_starter_us_east_2_stop_avg,
                                yesterday_zabbix_starter_us_east_eph_2_stop_max, yesterday_zabbix_starter_us_east_eph_2_stop_avg,
                                zabbix_starter_us_east_2_stop_max, zabbix_starter_us_east_2_stop_avg,
                                zabbix_starter_us_east_eph_2_stop_max, zabbix_starter_us_east_eph_2_stop_avg, a);
                        createAndSetFields(zabbix_starter_us_east_2_start_max, zabbix_starter_us_east_2_start_avg,
                                zabbix_starter_us_east_eph_2_start_max, zabbix_starter_us_east_eph_2_start_avg,
                                zabbix_starter_us_east_2_stop_max, zabbix_starter_us_east_2_stop_avg,
                                zabbix_starter_us_east_eph_2_stop_max, zabbix_starter_us_east_eph_2_stop_avg,
                                a, starter_2_changes, "2");
                        break;
                    case "STARTER_US_EAST_2A_COLOR":
                        Map<String, Float> starter_2a_changes = getMaxChangeAndSetColor(yesterday_zabbix_starter_us_east_2a_start_max, yesterday_zabbix_starter_us_east_2a_start_avg,
                                yesterday_zabbix_starter_us_east_eph_2a_start_max, yesterday_zabbix_starter_us_east_eph_2a_start_avg,
                                zabbix_starter_us_east_2a_start_max, zabbix_starter_us_east_2a_start_avg,
                                zabbix_starter_us_east_eph_2a_start_max, zabbix_starter_us_east_eph_2a_start_avg,
                                yesterday_zabbix_starter_us_east_2a_stop_max, yesterday_zabbix_starter_us_east_2a_stop_avg,
                                yesterday_zabbix_starter_us_east_eph_2a_stop_max, yesterday_zabbix_starter_us_east_eph_2a_stop_avg,
                                zabbix_starter_us_east_2a_stop_max, zabbix_starter_us_east_2a_stop_avg,
                                zabbix_starter_us_east_eph_2a_stop_max, zabbix_starter_us_east_eph_2a_stop_avg, a);
                        createAndSetFields(zabbix_starter_us_east_2a_start_max, zabbix_starter_us_east_2a_start_avg,
                                zabbix_starter_us_east_eph_2a_start_max, zabbix_starter_us_east_eph_2a_start_avg,
                                zabbix_starter_us_east_2a_stop_max, zabbix_starter_us_east_2a_stop_avg,
                                zabbix_starter_us_east_eph_2a_stop_max, zabbix_starter_us_east_eph_2a_stop_avg,
                                a, starter_2a_changes, "2a");
                        break;
                    case "STARTER_US_EAST_2A_PREVIEW_COLOR":
                        Map<String, Float> starter_2a_preview_changes = getMaxChangeAndSetColor(yesterday_zabbix_starter_us_east_preview_2a_start_max, yesterday_zabbix_starter_us_east_preview_2a_start_avg,
                                yesterday_zabbix_starter_us_east_eph_preview_2a_start_max, yesterday_zabbix_starter_us_east_eph_preview_2a_start_avg,
                                zabbix_starter_us_east_preview_2a_start_max, zabbix_starter_us_east_preview_2a_start_avg,
                                zabbix_starter_us_east_eph_preview_2a_start_max, zabbix_starter_us_east_eph_preview_2a_start_avg,
                                yesterday_zabbix_starter_us_east_preview_2a_stop_max, yesterday_zabbix_starter_us_east_preview_2a_stop_avg,
                                yesterday_zabbix_starter_us_east_eph_preview_2a_stop_max, yesterday_zabbix_starter_us_east_eph_preview_2a_stop_avg,
                                zabbix_starter_us_east_preview_2a_stop_max, zabbix_starter_us_east_preview_2a_stop_avg,
                                zabbix_starter_us_east_eph_preview_2a_stop_max, zabbix_starter_us_east_eph_preview_2a_stop_avg, a);
                        createAndSetFields(zabbix_starter_us_east_preview_2a_start_max, zabbix_starter_us_east_preview_2a_start_avg,
                                zabbix_starter_us_east_eph_preview_2a_start_max, zabbix_starter_us_east_eph_preview_2a_start_avg,
                                zabbix_starter_us_east_preview_2a_stop_max, zabbix_starter_us_east_preview_2a_stop_avg,
                                zabbix_starter_us_east_eph_preview_2a_stop_max, zabbix_starter_us_east_eph_preview_2a_stop_avg,
                                a, starter_2a_preview_changes, "2a preview");
                        break;
                    default: break;
                }
            }
            newAttachments.add(a);
        }
        slackPost.setAttachments(newAttachments);
        String channel = System.getenv("SLACK_CHANNEL");
        slackPost.setChannel(channel != null ? channel : "#devtools-che");
        LOG.info(gson.toJson(slackPost));

        try {
            HttpResponse tmp = slackWrapper.post("", ContentType.APPLICATION_JSON.toString(), gson.toJson(slackPost));
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
                    LOG.log(Level.SEVERE, "Failed to send slack message:"+response.grabContent());
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Failed to parse slack error response:"+e.getLocalizedMessage());
                }
            }
        }
    }

    private static void createAndSetFields(AtomicReference<Float> zabbix_starter_us_east_1a_start_max, AtomicReference<Float> zabbix_starter_us_east_1a_start_avg,
                                           AtomicReference<Float> zabbix_starter_us_east_eph_1a_start_max, AtomicReference<Float> zabbix_starter_us_east_eph_1a_start_avg,
                                           AtomicReference<Float> zabbix_starter_us_east_1a_stop_max, AtomicReference<Float> zabbix_starter_us_east_1a_stop_avg,
                                           AtomicReference<Float> zabbix_starter_us_east_eph_1a_stop_max, AtomicReference<Float> zabbix_starter_us_east_eph_1a_stop_avg,
                                           SlackPostAttachment a, Map<String, Float> cluster_changes, String clusterName) {
        List<SlackPostAttachmentField> cluster_fields = new ArrayList<>();
        String startTimesString = "*PVC* avg:" + String.format(" %.1fs", zabbix_starter_us_east_1a_start_avg.get()/1000) +
                ", max:" + String.format(" %.1fs", zabbix_starter_us_east_1a_start_max.get()/1000) + "\n" +
                "avg-diff:" + String.format(" %.2f", cluster_changes.get("pvc_start_avg")) + "%\n" +
                "*EPH* avg:" + String.format(" %.1fs", zabbix_starter_us_east_eph_1a_start_avg.get()/1000) +
                ", max:" + String.format(" %.1fs", zabbix_starter_us_east_eph_1a_start_max.get()/1000) + "\n" +
                "avg-diff:" + String.format(" %.2f", cluster_changes.get("eph_start_avg")) + "%";
        SlackPostAttachmentField cluster_start_times = new SlackPostAttachmentField("Cluster  "+clusterName+" start", startTimesString, true);
        String stopTimesString = "*PVC* avg:" + String.format(" %.1fs", zabbix_starter_us_east_1a_stop_avg.get()/1000) +
                ", max:" + String.format(" %.1fs", zabbix_starter_us_east_1a_stop_max.get()/1000) + "\n" +
                "avg-diff:" + String.format(" %.2f", cluster_changes.get("pvc_stop_avg")) + "%\n" +
                "*EPH* avg:" + String.format(" %.1fs", zabbix_starter_us_east_eph_1a_stop_avg.get()/1000) +
                ", max:" + String.format(" %.1fs", zabbix_starter_us_east_eph_1a_stop_max.get()/1000) + "\n" +
                "avg-diff:" + String.format(" %.2f", cluster_changes.get("eph_stop_avg")) + "%";
        SlackPostAttachmentField cluster_stop_times = new SlackPostAttachmentField("Cluster  "+clusterName+" stop", stopTimesString, true);
        cluster_fields.add(cluster_start_times);
        cluster_fields.add(cluster_stop_times);
        a.setFields(cluster_fields);
    }

    private static Map<String, Float> getMaxChangeAndSetColor(AtomicReference<Float> oldPvcStartMax,
                                                              AtomicReference<Float> oldPvcStartAvg,
                                                              AtomicReference<Float> oldEphStartMax,
                                                              AtomicReference<Float> oldEphStartAvg,
                                                              AtomicReference<Float> newPvcStartMax,
                                                              AtomicReference<Float> newPvcStartAvg,
                                                              AtomicReference<Float> newEphStartMax,
                                                              AtomicReference<Float> newEphStartAvg,
                                                              AtomicReference<Float> oldPvcStopMax,
                                                              AtomicReference<Float> oldPvcStopAvg,
                                                              AtomicReference<Float> oldEphStopMax,
                                                              AtomicReference<Float> oldEphStopAvg,
                                                              AtomicReference<Float> newPvcStopMax,
                                                              AtomicReference<Float> newPvcStopAvg,
                                                              AtomicReference<Float> newEphStopMax,
                                                              AtomicReference<Float> newEphStopAvg,
                                                              SlackPostAttachment a) {
        Map<String, Float> changes = getChangesMap(
                oldPvcStartMax, oldPvcStartAvg,
                oldEphStartMax, oldEphStartAvg,
                newPvcStartMax, newPvcStartAvg,
                newEphStartMax, newEphStartAvg,
                oldPvcStopMax, oldPvcStopAvg,
                oldEphStopMax, oldEphStopAvg,
                newPvcStopMax, newPvcStopAvg,
                newEphStopMax, newEphStopAvg);
        if (oldEphStartAvg.get() != MINMAX_INIT_VALUE &&
            oldEphStartMax.get() != MINMAX_INIT_VALUE &&
            oldPvcStartAvg.get() != MINMAX_INIT_VALUE &&
            oldPvcStartMax.get() != MINMAX_INIT_VALUE &&
            newEphStartAvg.get() != MINMAX_INIT_VALUE &&
            newEphStartMax.get() != MINMAX_INIT_VALUE &&
            newPvcStartAvg.get() != MINMAX_INIT_VALUE &&
            newPvcStartMax.get() != MINMAX_INIT_VALUE &&
            oldEphStopAvg.get() != MINMAX_INIT_VALUE &&
            oldEphStopMax.get() != MINMAX_INIT_VALUE &&
            oldPvcStopAvg.get() != MINMAX_INIT_VALUE &&
            oldPvcStopMax.get() != MINMAX_INIT_VALUE &&
            newEphStopAvg.get() != MINMAX_INIT_VALUE &&
            newEphStopMax.get() != MINMAX_INIT_VALUE &&
            newPvcStopAvg.get() != MINMAX_INIT_VALUE &&
            newPvcStopMax.get() != MINMAX_INIT_VALUE) {
            float percentageChange = getMaxChange(changes);
            LOG.info("Average diff percentage:" + percentageChange + " color:" + getColorBasedOnPercentage(percentageChange));
            a.setColor(getColorBasedOnPercentage(percentageChange));
        } else {
            a.setColor(null);
        }
        return changes;
    }

    private static String getColorBasedOnPercentage(float percentage) {
        if (percentage > SLACK_BROKEN_PERCENTAGE) return SLACK_BROKEN_COLOR;
        if (percentage > SLACK_UNSTABLE_PERCENTAGE) return SLACK_UNSTABLE_COLOR;
        return SLACK_SUCCESS_COLOR;
    }

    private static float getMaxChange(Map<String,Float> changes) {
        return Math.max(changes.get("pvc_start_avg"),
                    Math.max(changes.get("eph_start_avg"),
                    Math.max(changes.get("pvc_stop_avg"), changes.get("eph_stop_avg")
               )));
    }

    private static Map<String,Float> getChangesMap(AtomicReference<Float> oldPvcStartMax,  AtomicReference<Float> oldPvcStartAvg,
                                                   AtomicReference<Float> oldEphStartMax,  AtomicReference<Float> oldEphStartAvg,
                                                   AtomicReference<Float> newPvcStartMax,  AtomicReference<Float> newPvcStartAvg,
                                                   AtomicReference<Float> newEphStartMax,  AtomicReference<Float> newEphStartAvg,
                                                   AtomicReference<Float> oldPvcStopMax,  AtomicReference<Float> oldPvcStopAvg,
                                                   AtomicReference<Float> oldEphStopMax,  AtomicReference<Float> oldEphStopAvg,
                                                   AtomicReference<Float> newPvcStopMax,  AtomicReference<Float> newPvcStopAvg,
                                                   AtomicReference<Float> newEphStopMax,  AtomicReference<Float> newEphStopAvg) {
        Map<String, Float> changes = new HashMap<>();
        changes.put("pvc_start_avg", getPercentageDifference(oldPvcStartAvg, newPvcStartAvg));
        changes.put("pvc_start_max", getPercentageDifference(oldPvcStartMax, newPvcStartMax));
        changes.put("eph_start_avg", getPercentageDifference(oldEphStartAvg, newEphStartAvg));
        changes.put("eph_start_max", getPercentageDifference(oldEphStartMax, newEphStartMax));
        changes.put("pvc_stop_avg", getPercentageDifference(oldPvcStopAvg, newPvcStopAvg));
        changes.put("pvc_stop_max", getPercentageDifference(oldPvcStopMax, newPvcStopMax));
        changes.put("eph_stop_avg", getPercentageDifference(oldEphStopAvg, newEphStopAvg));
        changes.put("eph_stop_max", getPercentageDifference(oldEphStopMax, newEphStopMax));
        return changes;
    }

    private static void storeZabbixValueForComparison(AtomicReference<Float> storeValue, AtomicReference<Float> currentValue) {
        storeValue.set(currentValue.get());
        currentValue.set(MINMAX_INIT_VALUE);
    }

    private static float getPercentageDifference(AtomicReference<Float> oldValue, AtomicReference<Float> newValue) {
        return (newValue.get() - oldValue.get()) / oldValue.get() * 100;
    }

    private static boolean grabHistoryDataFromZabbix(HttpRequestWrapper wrapper, HttpRequestWrapperResponse response, JSONRPCRequest getHistoryRequest, List<ZabbixHistoryMetricsEntry> zabbixHistoryResults) {
        try {
            HttpResponse tmp = wrapper.post("/api_jsonrpc.php", ContentType.APPLICATION_JSON.toString(), getHistoryRequest.toString());
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
                    tmp.forEach(e -> zabbixHistoryResults.add(gson.fromJson(e, ZabbixHistoryMetricsEntry.class)));
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Failed to parse response into value list:"+e.getLocalizedMessage());
                    return false;
                }
            } else {
                LOG.log(Level.SEVERE, "Zabbix getHistory request failed.");
                return false;
            }
        }
        return true;
    }

    private static void calculateZabbixResults(List<ZabbixHistoryMetricsEntry> zabbixHistoryResults,
                                               AtomicReference<Float> zabbix_starter_us_east_1a_start_max, AtomicReference<Float> zabbix_starter_us_east_1b_start_max, AtomicReference<Float> zabbix_starter_us_east_2_start_max, AtomicReference<Float> zabbix_starter_us_east_2a_start_max, AtomicReference<Float> zabbix_starter_us_east_preview_2a_start_max,
                                               AtomicReference<Float> zabbix_starter_us_east_1a_start_avg, AtomicReference<Float> zabbix_starter_us_east_1b_start_avg, AtomicReference<Float> zabbix_starter_us_east_2_start_avg, AtomicReference<Float> zabbix_starter_us_east_2a_start_avg, AtomicReference<Float> zabbix_starter_us_east_preview_2a_start_avg,
                                               AtomicReference<Float> zabbix_starter_us_east_1a_stop_max, AtomicReference<Float> zabbix_starter_us_east_1b_stop_max, AtomicReference<Float> zabbix_starter_us_east_2_stop_max, AtomicReference<Float> zabbix_starter_us_east_2a_stop_max, AtomicReference<Float> zabbix_starter_us_east_preview_2a_stop_max,
                                               AtomicReference<Float> zabbix_starter_us_east_1a_stop_avg, AtomicReference<Float> zabbix_starter_us_east_1b_stop_avg, AtomicReference<Float> zabbix_starter_us_east_2_stop_avg, AtomicReference<Float> zabbix_starter_us_east_2a_stop_avg, AtomicReference<Float> zabbix_starter_us_east_preview_2a_stop_avg,
                                               AtomicReference<Float> zabbix_starter_us_east_eph_1a_start_max, AtomicReference<Float> zabbix_starter_us_east_eph_1b_start_max, AtomicReference<Float> zabbix_starter_us_east_eph_2_start_max, AtomicReference<Float> zabbix_starter_us_east_eph_2a_start_max, AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_start_max,
                                               AtomicReference<Float> zabbix_starter_us_east_eph_1a_start_avg, AtomicReference<Float> zabbix_starter_us_east_eph_1b_start_avg, AtomicReference<Float> zabbix_starter_us_east_eph_2_start_avg, AtomicReference<Float> zabbix_starter_us_east_eph_2a_start_avg, AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_start_avg,
                                               AtomicReference<Float> zabbix_starter_us_east_eph_1a_stop_max, AtomicReference<Float> zabbix_starter_us_east_eph_1b_stop_max, AtomicReference<Float> zabbix_starter_us_east_eph_2_stop_max, AtomicReference<Float> zabbix_starter_us_east_eph_2a_stop_max, AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_stop_max,
                                               AtomicReference<Float> zabbix_starter_us_east_eph_1a_stop_avg, AtomicReference<Float> zabbix_starter_us_east_eph_1b_stop_avg, AtomicReference<Float> zabbix_starter_us_east_eph_2_stop_avg, AtomicReference<Float> zabbix_starter_us_east_eph_2a_stop_avg, AtomicReference<Float> zabbix_starter_us_east_eph_preview_2a_stop_avg) {
        for (ZabbixHistoryMetricsEntry e : zabbixHistoryResults) {
            switch (e.getItemid()) {
                case ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_1a:
                    eph_cycles_count.set(eph_cycles_count.get() + 1);
                    updateValues(zabbix_starter_us_east_eph_1a_start_max,
                            zabbix_starter_us_east_eph_1a_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_1b:
                    updateValues(zabbix_starter_us_east_eph_1b_start_max,
                            zabbix_starter_us_east_eph_1b_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_2:
                    updateValues(zabbix_starter_us_east_eph_2_start_max,
                            zabbix_starter_us_east_eph_2_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_2a:
                    updateValues(zabbix_starter_us_east_eph_2a_start_max,
                            zabbix_starter_us_east_eph_2a_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_EPHEMERAL_ID_PROD_PREVIEW_2a:
                    updateValues(zabbix_starter_us_east_eph_preview_2a_start_max,
                            zabbix_starter_us_east_eph_preview_2a_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_1a:
                    updateValues(zabbix_starter_us_east_eph_1a_stop_max,
                            zabbix_starter_us_east_eph_1a_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_1b:
                    updateValues(zabbix_starter_us_east_eph_1b_stop_max,
                            zabbix_starter_us_east_eph_1b_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_2:
                    updateValues(zabbix_starter_us_east_eph_2_stop_max,
                            zabbix_starter_us_east_eph_2_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_2a:
                    updateValues(zabbix_starter_us_east_eph_2a_stop_max,
                            zabbix_starter_us_east_eph_2a_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_EPHEMERAL_ID_PROD_PREVIEW_2a:
                    updateValues(zabbix_starter_us_east_eph_preview_2a_stop_max,
                            zabbix_starter_us_east_eph_preview_2a_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_ID_PROD_1a:
                    pvc_cycles_count.set(pvc_cycles_count.get() + 1);
                    updateValues(zabbix_starter_us_east_1a_start_max,
                            zabbix_starter_us_east_1a_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_ID_PROD_1b:
                    updateValues(zabbix_starter_us_east_1b_start_max,
                            zabbix_starter_us_east_1b_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_ID_PROD_2:
                    updateValues(zabbix_starter_us_east_2_start_max,
                            zabbix_starter_us_east_2_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_ID_PROD_2a:
                    updateValues(zabbix_starter_us_east_2a_start_max,
                            zabbix_starter_us_east_2a_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_START_TIME_ID_PROD_PREVIEW_2a:
                    updateValues(zabbix_starter_us_east_preview_2a_start_max,
                            zabbix_starter_us_east_preview_2a_start_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_1a:
                    updateValues(zabbix_starter_us_east_1a_stop_max,
                            zabbix_starter_us_east_1a_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_1b:
                    updateValues(zabbix_starter_us_east_1b_stop_max,
                            zabbix_starter_us_east_1b_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_2:
                    updateValues(zabbix_starter_us_east_2_stop_max,
                            zabbix_starter_us_east_2_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_2a:
                    updateValues(zabbix_starter_us_east_2a_stop_max,
                            zabbix_starter_us_east_2a_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                case ZABBIX_WORKSPACE_STOP_TIME_ID_PROD_PREVIEW_2a:
                    updateValues(zabbix_starter_us_east_preview_2a_stop_max,
                            zabbix_starter_us_east_preview_2a_stop_avg,
                            Float.valueOf(e.getValue()));
                    break;
                default: break;
            }
        }
        if (zabbix_starter_us_east_1a_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_1a_start_avg.set(zabbix_starter_us_east_1a_start_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_1b_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_1b_start_avg.set(zabbix_starter_us_east_1b_start_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_2_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_2_start_avg.set(zabbix_starter_us_east_2_start_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_2a_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_2a_start_avg.set(zabbix_starter_us_east_2a_start_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_preview_2a_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_preview_2a_start_avg.set(zabbix_starter_us_east_preview_2a_start_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_1a_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_1a_stop_avg.set(zabbix_starter_us_east_1a_stop_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_1b_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_1b_stop_avg.set(zabbix_starter_us_east_1b_stop_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_2_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_2_stop_avg.set(zabbix_starter_us_east_2_stop_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_2a_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_2a_stop_avg.set(zabbix_starter_us_east_2a_stop_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_preview_2a_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_preview_2a_stop_avg.set(zabbix_starter_us_east_preview_2a_stop_avg.get() / pvc_cycles_count.get());
        if (zabbix_starter_us_east_eph_1a_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_1a_start_avg.set(zabbix_starter_us_east_eph_1a_start_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_1b_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_1b_start_avg.set(zabbix_starter_us_east_eph_1b_start_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_2_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_2_start_avg.set(zabbix_starter_us_east_eph_2_start_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_2a_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_2a_start_avg.set(zabbix_starter_us_east_eph_2a_start_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_preview_2a_start_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_preview_2a_start_avg.set(zabbix_starter_us_east_eph_preview_2a_start_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_1a_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_1a_stop_avg.set(zabbix_starter_us_east_eph_1a_stop_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_1b_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_1b_stop_avg.set(zabbix_starter_us_east_eph_1b_stop_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_2_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_2_stop_avg.set(zabbix_starter_us_east_eph_2_stop_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_2a_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_2a_stop_avg.set(zabbix_starter_us_east_eph_2a_stop_avg.get() / eph_cycles_count.get());
        if (zabbix_starter_us_east_eph_preview_2a_stop_avg.get() != MINMAX_INIT_VALUE)
            zabbix_starter_us_east_eph_preview_2a_stop_avg.set(zabbix_starter_us_east_eph_preview_2a_stop_avg.get() / eph_cycles_count.get());
    }

    private static boolean responseSuccess(HttpRequestWrapperResponse response) {
        int responseStatusCode = response.getStatusCode();
        return responseStatusCode == 200;
    }

    private static void updateValues(AtomicReference<Float> max, AtomicReference<Float> avg, float value) {
        if (max.get() == MINMAX_INIT_VALUE) {
            max.set(value);
        } else {
            if (max.get() < value) {
                max.set(value);
            }
        }
        if (avg.get() == MINMAX_INIT_VALUE) {
            avg.set(value);
        } else {
            avg.set(avg.get() + value);
        }
    }
}
