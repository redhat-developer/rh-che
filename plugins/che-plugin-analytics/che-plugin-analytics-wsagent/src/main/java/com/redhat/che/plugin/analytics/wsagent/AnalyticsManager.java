/*
 * Copyright (c) 2016-2018 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */

package com.redhat.che.plugin.analytics.wsagent;

import static com.redhat.che.plugin.analytics.wsagent.AnalyticsEvent.*;
import static com.redhat.che.plugin.analytics.wsagent.EventProperties.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.slf4j.LoggerFactory.getLogger;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.segment.analytics.Analytics;
import com.segment.analytics.messages.TrackMessage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.workspace.shared.dto.WorkspaceDto;
import org.slf4j.Logger;

/**
 * Send event to Segment.com and regularly ping Woopra. For now the events are provided by the
 * {@link UrlToEventFilter}.
 *
 * @author David Festal
 */
@Singleton
public class AnalyticsManager {
  private static final Logger LOG = getLogger(AnalyticsManager.class);

  private static final String pingRequestFormat =
      "http://www.woopra.com/track/ping?host={0}&cookie={1}&timeout={2}";

  private final Analytics analytics;

  private final long noActivityTimeout = 60000 * 3;
  private final long pingTimeout = 30 * 1000;
  private final String workspaceId;
  private final String workspaceName;

  private String segmentWriteKey;
  private String woopraDomain;

  private ScheduledExecutorService checkActivityExecutor =
      Executors.newSingleThreadScheduledExecutor(
          new ThreadFactoryBuilder().setNameFormat("Analytics Activity Checker").build());

  private ScheduledExecutorService networkExecutor =
      Executors.newSingleThreadScheduledExecutor(
          new ThreadFactoryBuilder().setNameFormat("Analytics Network Request Submitter").build());

  private LoadingCache<String, EventDispatcher> dispatchers;

  private String workspaceStartingUserId = null;

  @Inject
  public AnalyticsManager(
      @Named("env.CHE_WORKSPACE_ID") String workspaceId,
      HttpJsonRequestFactory requestFactory,
      @Named("che.api") String apiEndpoint) {
    try {
      String endpoint = apiEndpoint + "/fabric8-che-analytics/segment-write-key";
      segmentWriteKey = requestFactory.fromUrl(endpoint).request().asString();

      endpoint = apiEndpoint + "/fabric8-che-analytics/woopra-domain";
      woopraDomain = requestFactory.fromUrl(endpoint).request().asString();
    } catch (Exception e) {
      throw new RuntimeException("Can't get Che analytics settings from wsmaster", e);
    }

    try {
      String endpoint = apiEndpoint + "/workspace/" + workspaceId;
      workspaceName =
          requestFactory
              .fromUrl(endpoint)
              .request()
              .asDto(WorkspaceDto.class)
              .getConfig()
              .getName();
    } catch (Exception e) {
      throw new RuntimeException("Can't get workspace name for Che analytics", e);
    }

    if (!segmentWriteKey.isEmpty() && woopraDomain.isEmpty()) {
      throw new RuntimeException(
          "The Woopra domain should be set to provide better visit tracking and duration calculation");
    }

    if (isEnabled()) {
      analytics =
          Analytics.builder(segmentWriteKey)
              .networkExecutor(networkExecutor)
              .flushQueueSize(1)
              .build();
    } else {
      analytics = null;
    }

    this.workspaceId = workspaceId;

    checkActivityExecutor.scheduleAtFixedRate(this::checkActivity, 20, 20, SECONDS);

    dispatchers =
        CacheBuilder.newBuilder()
            .build(CacheLoader.<String, EventDispatcher>from(userId -> newEventDispatcher(userId)));
  }

  private EventDispatcher newEventDispatcher(String userId) {
    return new EventDispatcher(userId, this);
  }

  public String getWorkspaceId() {
    return workspaceId;
  }

  public boolean isEnabled() {
    return !segmentWriteKey.isEmpty();
  }

  public Analytics getAnalytics() {
    return analytics;
  }

  private void checkActivity() {
    LOG.debug("In checkActivity");
    long inactiveLimit = System.currentTimeMillis() - noActivityTimeout;
    dispatchers
        .asMap()
        .values()
        .forEach(
            dispatcher -> {
              LOG.debug("Checking activity of dispatcher for user: {}", dispatcher.getUserId());
              if (dispatcher.getLastActivityTime() < inactiveLimit) {
                LOG.debug(
                    "Sending 'WORKSPACE_INACTIVE' event for user: {}", dispatcher.getUserId());
                if (dispatcher.sendTrackEvent(
                        WORKSPACE_INACTIVE,
                        Collections.emptyMap(),
                        dispatcher.getLastIp(),
                        dispatcher.getLastUserAgent())
                    != null) {
                  LOG.debug("Sent 'WORKSPACE_INACTIVE' event for user: {}", dispatcher.getUserId());
                  return;
                }
                LOG.debug(
                    "Skip sending 'WORKSPACE_INACTIVE' event for user: {} since it is the same event as the previous one",
                    dispatcher.getUserId());
              } else {
                synchronized (dispatcher) {
                  AnalyticsEvent lastEvent = dispatcher.getLastEvent();
                  if (lastEvent == null) {
                    return;
                  }

                  long expectedDuration = lastEvent.getExpectedDurationSeconds() * 1000;
                  if (lastEvent == WORKSPACE_INACTIVE
                      || (expectedDuration >= 0
                          && System.currentTimeMillis()
                              > expectedDuration + dispatcher.getLastEventTime())) {
                    if (dispatcher.sendTrackEvent(
                            WORKSPACE_USED,
                            Collections.emptyMap(),
                            dispatcher.getLastIp(),
                            dispatcher.getLastUserAgent())
                        != null) {
                      return;
                    }
                  }
                }
              }

              networkExecutor.submit(dispatcher::sendPingRequest);
            });
  }

  public void onActivity(String userId) {
    try {
      dispatchers.get(userId).onActivity();
    } catch (ExecutionException e) {
      LOG.warn("", e);
    }
  }

  public void onEvent(
      String userId,
      AnalyticsEvent event,
      Map<String, Object> properties,
      String ip,
      String userAgent) {
    if (event == WORKSPACE_STARTED) {
      workspaceStartingUserId = userId;
    }
    try {
      dispatchers.get(userId).sendTrackEvent(event, properties, ip, userAgent);
    } catch (ExecutionException e) {
      LOG.warn("", e);
    }
    ;
  }

  private class EventDispatcher {

    private String userId;
    private String cookie;

    private AnalyticsEvent lastEvent = null;
    private Map<String, Object> lastEventProperties = null;
    private long lastActivityTime;
    private long lastEventTime;
    private String lastIp = null;
    private String lastUserAgent = null;

    private Map<String, Object> commonProperties;

    EventDispatcher(String userId, AnalyticsManager manager) {
      this.userId = userId;
      this.commonProperties =
          ImmutableMap.<String, Object>of(
              WORKSPACE_ID, workspaceId,
              WORKSPACE_NAME, workspaceName);
      this.cookie =
          Hashing.md5()
              .hashString(workspaceId + userId + System.currentTimeMillis(), StandardCharsets.UTF_8)
              .toString();
      LOG.info(
          "Analytics Woopra Cookie for user {} and workspace {} : {}", userId, workspaceId, cookie);
    }

    void onActivity() {
      lastActivityTime = System.currentTimeMillis();
    }

    void sendPingRequest() {
      try {
        URI uri =
            new URI(
                MessageFormat.format(
                    pingRequestFormat,
                    URLEncoder.encode(woopraDomain, "UTF-8"),
                    URLEncoder.encode(cookie, "UTF-8"),
                    Long.toString(pingTimeout)));
        LOG.debug("Sending a PING request to woopra for user '{}': {}", getUserId(), uri);
        HttpURLConnection httpURLConnection = (HttpURLConnection) uri.toURL().openConnection();

        String responseMessage;
        try (BufferedReader br =
                new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
            StringWriter sw = new StringWriter()) {
          String inputLine;

          while ((inputLine = br.readLine()) != null) {
            sw.write(inputLine);
          }
          responseMessage = sw.toString();
        }
        LOG.debug("Woopra PING response for user '{}': {}", userId, responseMessage);
        if (responseMessage == null || !responseMessage.toString().contains("success: true")) {
          LOG.warn("Cannot ping woopra: response message : {}", responseMessage);
        }
      } catch (Exception e) {
        LOG.warn("Cannot ping woopra", e);
      }
    }

    private boolean areEventsEqual(AnalyticsEvent event, Map<String, Object> properties) {
      if (lastEvent == null || lastEvent != event) {
        return false;
      }

      if (lastEventProperties == null) {
        return false;
      }

      for (String propToCheck : event.getPropertiesToCheck()) {
        Object lastValue = lastEventProperties.get(propToCheck);
        Object newValue = properties.get(propToCheck);
        if (lastValue != null && newValue != null && lastValue.equals(newValue)) {
          continue;
        }
        if (lastValue == null && newValue == null) {
          continue;
        }
        return false;
      }

      return true;
    }

    String sendTrackEvent(
        AnalyticsEvent event, final Map<String, Object> properties, String ip, String userAgent) {
      String eventId;
      lastIp = ip;
      lastUserAgent = userAgent;
      final String theIp = ip != null ? ip : "0.0.0.0";
      synchronized (this) {
        lastEventTime = System.currentTimeMillis();
        if (areEventsEqual(event, properties)) {
          LOG.debug("Skipping event " + event.toString() + " since it is the same as the last one");
          return null;
        }

        eventId = UUID.randomUUID().toString();
        TrackMessage.Builder messageBuilder =
            TrackMessage.builder(event.toString()).userId(userId).messageId(eventId);

        ImmutableMap.Builder<String, Object> integrationBuilder =
            ImmutableMap.<String, Object>builder().put("cookie", cookie);
        if (event.getExpectedDurationSeconds() == 0) {
          integrationBuilder.put("duration", 0);
        }
        messageBuilder.integrationOptions("Woopra", integrationBuilder.build());

        ImmutableMap.Builder<String, Object> propertiesBuilder =
            ImmutableMap.<String, Object>builder().putAll(commonProperties).putAll(properties);
        messageBuilder.properties(propertiesBuilder.build());

        ImmutableMap.Builder<String, Object> contextBuilder =
            ImmutableMap.<String, Object>builder().put("ip", ip);
        if (userAgent != null) {
          contextBuilder.put("userAgent", userAgent);
        }
        if (event.getExpectedDurationSeconds() == 0) {
          contextBuilder.put("duration", 0);
        }
        messageBuilder.context(contextBuilder.build());

        LOG.debug(
            "sending "
                + event.toString()
                + " (ip="
                + theIp
                + " - userAgent="
                + userAgent
                + ") with properties: "
                + properties);
        analytics.enqueue(messageBuilder);

        lastEvent = event;
        lastEventProperties = properties;
      }
      return eventId;
    }

    long getLastActivityTime() {
      return lastActivityTime;
    }

    String getLastIp() {
      return lastIp;
    }

    String getLastUserAgent() {
      return lastUserAgent;
    }

    String getUserId() {
      return userId;
    }

    AnalyticsEvent getLastEvent() {
      return lastEvent;
    }

    long getLastEventTime() {
      return lastEventTime;
    }
  }

  @PreDestroy
  void destroy() {
    if (workspaceStartingUserId != null) {
      EventDispatcher dispatcher;
      try {
        dispatcher = dispatchers.get(workspaceStartingUserId);
        dispatcher.sendTrackEvent(
            WORKSPACE_STOPPED,
            Collections.emptyMap(),
            dispatcher.getLastIp(),
            dispatcher.getLastUserAgent());
      } catch (ExecutionException e) {
      }
    }
    checkActivityExecutor.shutdown();
  }
}
