/*
 * Copyright (c) 2016-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.che.plugin.analytics.wsagent;

import static com.google.common.collect.ImmutableMap.builder;
import static com.google.common.collect.ImmutableMap.of;
import static com.redhat.che.plugin.analytics.wsagent.AnalyticsEvent.*;
import static java.util.Collections.emptyMap;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.redhat.che.plugin.analytics.wsagent.AnalyticsManager.EventDispatcher;
import com.segment.analytics.Analytics;
import com.segment.analytics.messages.TrackMessage;
import com.segment.analytics.messages.TrackMessage.Builder;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import org.eclipse.che.api.core.rest.HttpJsonRequest;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.api.core.rest.HttpJsonResponse;
import org.eclipse.che.api.factory.shared.dto.AuthorDto;
import org.eclipse.che.api.factory.shared.dto.FactoryDto;
import org.eclipse.che.api.workspace.shared.Constants;
import org.eclipse.che.api.workspace.shared.dto.WorkspaceConfigDto;
import org.eclipse.che.api.workspace.shared.dto.WorkspaceDto;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class AnalyticsManagerTest {

  private static String WORKSPACE_ID = "theWorkspaceId";
  private static String WORKSPACE_NAME = "theWorkspaceName";
  private static Long AGE = 60L;
  private static Long RETURN_DELAY = 30L;
  private static Long CREATED_ON_TIMESTAMP = System.currentTimeMillis();
  private static Long UPDATED_ON_TIMESTAMP = System.currentTimeMillis() + AGE * 1000;
  private static Long STOPPED_ON_TIMESTAMP = UPDATED_ON_TIMESTAMP - RETURN_DELAY * 1000;
  private static String CREATED_ON = Long.toString(CREATED_ON_TIMESTAMP);
  private static String UPDATED_ON = Long.toString(UPDATED_ON_TIMESTAMP);
  private static String STOPPED_ON = Long.toString(STOPPED_ON_TIMESTAMP);
  private static String STACK_ID = "theStackId";
  private static String FACTORY_ID = "theFactoryId";
  private static String FACTORY_NAME = "theFactoryName";
  private static String FACTORY_OWNER = "theFactoryOwner";
  private static String API_ENDPOINT = "http://che-host:8080/api";
  private static String SEGMENT_WRITE_KEY = "TheSegmentWriteKey";
  private static String WOOPRA_DOMAIN = "TheWoopraDomain";
  private static String USER_ID = "theUserId";
  private static String IP = "theIp";
  private static String USER_AGENT = "theUserAgent";

  @Mock private HttpJsonRequestFactory requestFactory;

  @Mock private HttpJsonRequest requestToGetSegmentWriteKey;
  @Mock private HttpJsonRequest requestToGetWoopraDomain;
  @Mock private HttpJsonRequest requestToGetWorkspaceName;
  @Mock private HttpJsonRequest requestToGetFactoryDetail;
  @Mock private HttpJsonResponse segmentWriteKeyResponse;
  @Mock private HttpJsonResponse woopraDomainResponse;
  @Mock private HttpJsonResponse workspaceNameResponse;
  @Mock private HttpJsonResponse factoryDetailResponse;
  @Mock private WorkspaceDto workspaceDto;
  @Mock private Map<String, String> workspaceAttributes;
  @Mock private FactoryDto factoryDto;
  @Mock private AuthorDto authorDto;
  @Mock private WorkspaceConfigDto workspaceConfigDto;
  @Mock private Analytics analytics;
  @Mock private AnalyticsProvider analyticsProvider;
  @Mock private HttpUrlConnectionProvider httpUrlConnectionProvider;
  @Mock private HttpURLConnection urlConnection;

  private AnalyticsManager manager;

  static {
    AnalyticsManager.pingTimeoutSeconds = 10;
  }

  @BeforeMethod
  public void setUp() throws Exception {
    doReturn(requestToGetSegmentWriteKey)
        .when(requestFactory)
        .fromUrl(API_ENDPOINT + "/fabric8-che-analytics/segment-write-key");
    doReturn(requestToGetWoopraDomain)
        .when(requestFactory)
        .fromUrl(API_ENDPOINT + "/fabric8-che-analytics/woopra-domain");
    doReturn(requestToGetWorkspaceName)
        .when(requestFactory)
        .fromUrl(API_ENDPOINT + "/workspace/" + WORKSPACE_ID);
    doReturn(requestToGetFactoryDetail)
        .when(requestFactory)
        .fromUrl(API_ENDPOINT + "/factory/" + FACTORY_ID);
    when(requestToGetSegmentWriteKey.request()).thenReturn(segmentWriteKeyResponse);
    when(requestToGetWoopraDomain.request()).thenReturn(woopraDomainResponse);
    when(requestToGetWorkspaceName.request()).thenReturn(workspaceNameResponse);
    when(requestToGetFactoryDetail.request()).thenReturn(factoryDetailResponse);
    when(segmentWriteKeyResponse.asString()).thenReturn(SEGMENT_WRITE_KEY);
    when(woopraDomainResponse.asString()).thenReturn(WOOPRA_DOMAIN);
    when(workspaceNameResponse.asDto(WorkspaceDto.class)).thenReturn(workspaceDto);
    when(factoryDetailResponse.asDto(FactoryDto.class)).thenReturn(factoryDto);
    when(workspaceDto.getConfig()).thenReturn(workspaceConfigDto);
    when(workspaceDto.getAttributes()).thenReturn(workspaceAttributes);
    doReturn(CREATED_ON).when(workspaceAttributes).get(Constants.CREATED_ATTRIBUTE_NAME);
    doReturn(UPDATED_ON).when(workspaceAttributes).get(Constants.UPDATED_ATTRIBUTE_NAME);
    doReturn(STOPPED_ON).when(workspaceAttributes).get(Constants.STOPPED_ATTRIBUTE_NAME);
    when(workspaceConfigDto.getName()).thenReturn(WORKSPACE_NAME);
    when(factoryDto.getName()).thenReturn(FACTORY_NAME);
    when(factoryDto.getCreator()).thenReturn(authorDto);
    when(authorDto.getName()).thenReturn(FACTORY_OWNER);
    when(analyticsProvider.getAnalytics(any(), any())).thenReturn(analytics);
    when(httpUrlConnectionProvider.getHttpUrlConnection(any())).thenReturn(urlConnection);
    when(urlConnection.getInputStream())
        .then(inv -> new ByteArrayInputStream("success: true".getBytes("UTF-8")));
  }

  @AfterMethod
  public void cleanUp() {
    if (manager != null) {
      manager.networkExecutor.shutdownNow();
      manager.shutdown();
    }
  }

  @Test
  public void constructWithValidValues() throws Exception {
    manager =
        new AnalyticsManager(
            WORKSPACE_ID,
            requestFactory,
            API_ENDPOINT,
            analyticsProvider,
            httpUrlConnectionProvider);
    assertEquals(manager.isEnabled(), true);
    assertEquals(manager.workspaceName, WORKSPACE_NAME);
    assertNotNull(manager.getAnalytics());
  }

  @Test
  public void testGetWorkspaceId() throws Exception {
    manager =
        new AnalyticsManager(
            WORKSPACE_ID,
            requestFactory,
            API_ENDPOINT,
            analyticsProvider,
            httpUrlConnectionProvider);
    assertEquals(manager.getWorkspaceId(), WORKSPACE_ID);
  }

  @Test(
      expectedExceptions = {RuntimeException.class},
      expectedExceptionsMessageRegExp = "Can't get Che analytics settings from wsmaster")
  public void constructWithExceptionDuringWriteKeyRetrieval() throws Exception {
    when(requestToGetSegmentWriteKey.request()).thenThrow(NotFoundException.class);
    new AnalyticsManager(
        WORKSPACE_ID, requestFactory, API_ENDPOINT, analyticsProvider, httpUrlConnectionProvider);
  }

  @Test(
      expectedExceptions = {RuntimeException.class},
      expectedExceptionsMessageRegExp = "Can't get Che analytics settings from wsmaster")
  public void constructWithExceptionDuringWoopraDomainRetrieval() throws Exception {
    when(requestToGetWoopraDomain.request()).thenThrow(NotFoundException.class);
    new AnalyticsManager(
        WORKSPACE_ID, requestFactory, API_ENDPOINT, analyticsProvider, httpUrlConnectionProvider);
  }

  @Test(
      expectedExceptions = {RuntimeException.class},
      expectedExceptionsMessageRegExp = "Can't get workspace informations for Che analytics")
  public void constructWithExceptionDuringWorkspaceNameRetrieval() throws Exception {
    when(requestToGetWorkspaceName.request()).thenThrow(NotFoundException.class);
    new AnalyticsManager(
        WORKSPACE_ID, requestFactory, API_ENDPOINT, analyticsProvider, httpUrlConnectionProvider);
  }

  @Test
  public void disableOnEmptySegmentKey() throws Exception {
    when(segmentWriteKeyResponse.asString()).thenReturn("");
    manager =
        new AnalyticsManager(
            WORKSPACE_ID,
            requestFactory,
            API_ENDPOINT,
            analyticsProvider,
            httpUrlConnectionProvider);
    assertEquals(manager.isEnabled(), false);
    assertNull(manager.getAnalytics());
  }

  @Test(
      expectedExceptions = {RuntimeException.class},
      expectedExceptionsMessageRegExp =
          "The Woopra domain should be set to provide better visit tracking and duration calculation")
  public void exceptionOnEmptyWoopraDomain() throws Exception {
    when(woopraDomainResponse.asString()).thenReturn("");
    new AnalyticsManager(
        WORKSPACE_ID, requestFactory, API_ENDPOINT, analyticsProvider, httpUrlConnectionProvider);
  }

  @Test
  public void storeStartingUserIdOnStartEvent() throws Exception {
    manager =
        new AnalyticsManager(
            WORKSPACE_ID,
            requestFactory,
            API_ENDPOINT,
            analyticsProvider,
            httpUrlConnectionProvider);
    manager.onEvent(USER_ID, WORKSPACE_STARTED, Collections.emptyMap(), IP, USER_AGENT);
    assertEquals(manager.workspaceStartingUserId, USER_ID);
  }

  @Test
  public void onEventSendsTrackEvent() throws Exception {
    manager =
        new AnalyticsManager(
            WORKSPACE_ID,
            requestFactory,
            API_ENDPOINT,
            analyticsProvider,
            httpUrlConnectionProvider);

    try {
      manager.onEvent(USER_ID, WORKSPACE_USED, Collections.emptyMap(), IP, USER_AGENT);

      ArgumentCaptor<TrackMessage.Builder> argCaptor =
          ArgumentCaptor.forClass(TrackMessage.Builder.class);
      verify(analytics).enqueue(argCaptor.capture());
      TrackMessage sentMessage = argCaptor.getValue().build();

      EventDispatcher dispatcher = manager.dispatchers.get(USER_ID);
      assertNotNull(dispatcher);
      assertEquals(dispatcher.userId, USER_ID);
      assertEquals(dispatcher.getLastIp(), IP);
      assertEquals(dispatcher.getLastUserAgent(), USER_AGENT);
      assertEquals(dispatcher.getLastEvent(), WORKSPACE_USED);
      assertEquals(dispatcher.lastEventProperties, Collections.emptyMap());

      assertEquals(sentMessage.event(), WORKSPACE_USED.toString());
      assertEquals(sentMessage.userId(), USER_ID);
      assertEquals(
          sentMessage.integrations(),
          of("Woopra", of("cookie", dispatcher.cookie, "timeout", AnalyticsManager.pingTimeout)));
      assertEquals(sentMessage.context(), of("ip", IP, "userAgent", USER_AGENT));
      assertEquals(
          sentMessage.properties(),
          builder()
              .put(EventProperties.WORKSPACE_ID, WORKSPACE_ID)
              .put(EventProperties.WORKSPACE_NAME, WORKSPACE_NAME)
              .put(EventProperties.CREATED, CREATED_ON)
              .put(EventProperties.UPDATED, UPDATED_ON)
              .put(EventProperties.STOPPED, STOPPED_ON)
              .put(EventProperties.AGE, AGE)
              .put(EventProperties.RETURN_DELAY, RETURN_DELAY)
              .put(EventProperties.FIRST_START, false)
              .build());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testFirstWorkspaceStart() throws Exception {
    when(workspaceAttributes.get(Constants.STOPPED_ATTRIBUTE_NAME)).thenReturn(null);
    manager =
        new AnalyticsManager(
            WORKSPACE_ID,
            requestFactory,
            API_ENDPOINT,
            analyticsProvider,
            httpUrlConnectionProvider);

    manager.onEvent(USER_ID, WORKSPACE_USED, Collections.emptyMap(), IP, USER_AGENT);

    ArgumentCaptor<TrackMessage.Builder> argCaptor =
        ArgumentCaptor.forClass(TrackMessage.Builder.class);
    verify(analytics).enqueue(argCaptor.capture());
    TrackMessage sentMessage = argCaptor.getValue().build();

    assertEquals(
        sentMessage.properties(),
        builder()
            .put(EventProperties.WORKSPACE_ID, WORKSPACE_ID)
            .put(EventProperties.WORKSPACE_NAME, WORKSPACE_NAME)
            .put(EventProperties.CREATED, CREATED_ON)
            .put(EventProperties.UPDATED, UPDATED_ON)
            .put(EventProperties.AGE, AGE)
            .put(EventProperties.FIRST_START, true)
            .build());
  }

  @Test
  public void sendStackIdIfThere() throws Exception {
    lenient().doReturn(STACK_ID).when(workspaceAttributes).get("stackId");
    manager =
        new AnalyticsManager(
            WORKSPACE_ID,
            requestFactory,
            API_ENDPOINT,
            analyticsProvider,
            httpUrlConnectionProvider);

    manager.onEvent(USER_ID, WORKSPACE_USED, Collections.emptyMap(), IP, USER_AGENT);

    ArgumentCaptor<TrackMessage.Builder> argCaptor =
        ArgumentCaptor.forClass(TrackMessage.Builder.class);
    verify(analytics).enqueue(argCaptor.capture());
    TrackMessage sentMessage = argCaptor.getValue().build();

    assertEquals(
        sentMessage.properties(),
        builder()
            .put(EventProperties.WORKSPACE_ID, WORKSPACE_ID)
            .put(EventProperties.WORKSPACE_NAME, WORKSPACE_NAME)
            .put(EventProperties.CREATED, CREATED_ON)
            .put(EventProperties.UPDATED, UPDATED_ON)
            .put(EventProperties.STOPPED, STOPPED_ON)
            .put(EventProperties.AGE, AGE)
            .put(EventProperties.RETURN_DELAY, RETURN_DELAY)
            .put(EventProperties.FIRST_START, false)
            .put(EventProperties.STACK_ID, STACK_ID)
            .build());
  }

  @Test
  public void sendFactoryInfosIfThere() throws Exception {
    lenient().doReturn(FACTORY_ID).when(workspaceAttributes).get("factoryId");
    manager =
        new AnalyticsManager(
            WORKSPACE_ID,
            requestFactory,
            API_ENDPOINT,
            analyticsProvider,
            httpUrlConnectionProvider);

    manager.onEvent(USER_ID, WORKSPACE_USED, Collections.emptyMap(), IP, USER_AGENT);

    ArgumentCaptor<TrackMessage.Builder> argCaptor =
        ArgumentCaptor.forClass(TrackMessage.Builder.class);
    verify(analytics).enqueue(argCaptor.capture());
    TrackMessage sentMessage = argCaptor.getValue().build();

    assertEquals(
        sentMessage.properties(),
        builder()
            .put(EventProperties.WORKSPACE_ID, WORKSPACE_ID)
            .put(EventProperties.WORKSPACE_NAME, WORKSPACE_NAME)
            .put(EventProperties.CREATED, CREATED_ON)
            .put(EventProperties.UPDATED, UPDATED_ON)
            .put(EventProperties.STOPPED, STOPPED_ON)
            .put(EventProperties.AGE, AGE)
            .put(EventProperties.RETURN_DELAY, RETURN_DELAY)
            .put(EventProperties.FIRST_START, false)
            .put(EventProperties.FACTORY_ID, FACTORY_ID)
            .put(EventProperties.FACTORY_NAME, FACTORY_NAME)
            .put(EventProperties.FACTORY_OWNER, FACTORY_OWNER)
            .build());
  }

  @Test
  public void onSameEventsSkipped() throws Exception {
    manager =
        new AnalyticsManager(
            WORKSPACE_ID,
            requestFactory,
            API_ENDPOINT,
            analyticsProvider,
            httpUrlConnectionProvider);

    manager.onEvent(USER_ID, WORKSPACE_USED, Collections.emptyMap(), IP, USER_AGENT);
    Thread.sleep(1);
    manager.onEvent(USER_ID, WORKSPACE_USED, Collections.emptyMap(), IP, USER_AGENT);

    ArgumentCaptor<TrackMessage.Builder> argCaptor =
        ArgumentCaptor.forClass(TrackMessage.Builder.class);
    verify(analytics, times(1)).enqueue(argCaptor.capture());

    TrackMessage sentMessage = argCaptor.getValue().build();
    assertEquals(sentMessage.event(), WORKSPACE_USED.toString());
  }

  @Test
  public void sameEventsWithDifferentPropertiesNotSkipped() throws Exception {
    manager =
        new AnalyticsManager(
            WORKSPACE_ID,
            requestFactory,
            API_ENDPOINT,
            analyticsProvider,
            httpUrlConnectionProvider);

    manager.onEvent(
        USER_ID, EDITOR_USED, of(EventProperties.PROGRAMMING_LANGUAGE, "java"), IP, USER_AGENT);
    Thread.sleep(1);
    manager.onEvent(
        USER_ID, EDITOR_USED, of(EventProperties.PROGRAMMING_LANGUAGE, "xml"), IP, USER_AGENT);

    verify(analytics, times(2)).enqueue(isEvent(EDITOR_USED));
  }

  @Test
  public void sendWorkspaceUsedAfterPreviousEventExpectedDurationElapsed() throws Exception {
    manager =
        new AnalyticsManager(
            WORKSPACE_ID,
            requestFactory,
            API_ENDPOINT,
            analyticsProvider,
            httpUrlConnectionProvider);

    manager.onActivity(USER_ID);
    manager.onEvent(USER_ID, WORKSPACE_OPENED, emptyMap(), IP, USER_AGENT);

    ArgumentCaptor<TrackMessage.Builder> argCaptor =
        ArgumentCaptor.forClass(TrackMessage.Builder.class);

    verify(analytics, timeout(AnalyticsManager.pingTimeout).atLeast(2))
        .enqueue(argCaptor.capture());

    assertEquals(
        capturedEventNames(argCaptor),
        Arrays.asList(WORKSPACE_OPENED.toString(), WORKSPACE_USED.toString()));
  }

  @Test
  public void sendWorkspaceUsedOnActivityAfterInactivity() throws Exception {
    manager =
        new AnalyticsManager(
            WORKSPACE_ID,
            requestFactory,
            API_ENDPOINT,
            analyticsProvider,
            httpUrlConnectionProvider);

    manager.onEvent(USER_ID, WORKSPACE_INACTIVE, emptyMap(), IP, USER_AGENT);
    manager.onActivity(USER_ID);

    ArgumentCaptor<TrackMessage.Builder> argCaptor =
        ArgumentCaptor.forClass(TrackMessage.Builder.class);

    verify(analytics, timeout(AnalyticsManager.pingTimeout).atLeast(2))
        .enqueue(argCaptor.capture());

    assertEquals(
        capturedEventNames(argCaptor),
        Arrays.asList(WORKSPACE_INACTIVE.toString(), WORKSPACE_USED.toString()));
  }

  @Test
  public void sendWorkspaceInactiveAfterActivityTimeoutElapsed() throws Exception {
    manager =
        new AnalyticsManager(
            WORKSPACE_ID,
            requestFactory,
            API_ENDPOINT,
            analyticsProvider,
            httpUrlConnectionProvider);
    manager.noActivityTimeout = 3 * 1000;

    manager.onActivity(USER_ID);
    manager.onEvent(USER_ID, WORKSPACE_USED, emptyMap(), IP, USER_AGENT);

    ArgumentCaptor<TrackMessage.Builder> argCaptor =
        ArgumentCaptor.forClass(TrackMessage.Builder.class);

    verify(analytics, timeout(AnalyticsManager.pingTimeout).atLeast(2))
        .enqueue(argCaptor.capture());

    assertEquals(
        capturedEventNames(argCaptor),
        Arrays.asList(WORKSPACE_USED.toString(), WORKSPACE_INACTIVE.toString()));
  }

  @Test
  public void sendWorkspaceStoppedOnDestroy() throws Exception {
    manager =
        new AnalyticsManager(
            WORKSPACE_ID,
            requestFactory,
            API_ENDPOINT,
            analyticsProvider,
            httpUrlConnectionProvider);

    manager.onActivity(USER_ID);
    manager.onEvent(USER_ID, WORKSPACE_STARTED, emptyMap(), IP, USER_AGENT);
    manager.destroy();

    ArgumentCaptor<TrackMessage.Builder> argCaptor =
        ArgumentCaptor.forClass(TrackMessage.Builder.class);

    verify(analytics, timeout(AnalyticsManager.pingTimeout).atLeast(2))
        .enqueue(argCaptor.capture());

    assertEquals(
        capturedEventNames(argCaptor),
        Arrays.asList(WORKSPACE_STARTED.toString(), WORKSPACE_STOPPED.toString()));
    assertEquals(argCaptor.getAllValues().get(1).build().userId(), USER_ID);
  }

  @Test
  public void sendPingRequest() throws Exception {
    manager =
        new AnalyticsManager(
            WORKSPACE_ID,
            requestFactory,
            API_ENDPOINT,
            analyticsProvider,
            httpUrlConnectionProvider);

    manager.onActivity(USER_ID);
    manager.onEvent(USER_ID, WORKSPACE_USED, emptyMap(), IP, USER_AGENT);

    ArgumentCaptor<TrackMessage.Builder> argCaptor =
        ArgumentCaptor.forClass(TrackMessage.Builder.class);

    verify(analytics, timeout(AnalyticsManager.pingTimeout).atLeast(1))
        .enqueue(argCaptor.capture());

    assertEquals(capturedEventNames(argCaptor), Arrays.asList(WORKSPACE_USED.toString()));

    EventDispatcher dispatcher = manager.dispatchers.get(USER_ID);

    verify(urlConnection, timeout(AnalyticsManager.pingTimeout)).getInputStream();

    String expectedUriString =
        "http://www.woopra.com/track/ping?host="
            + URLEncoder.encode(WOOPRA_DOMAIN, "UTF-8")
            + "&cookie="
            + URLEncoder.encode(dispatcher.cookie, "UTF-8")
            + "&timeout="
            + Long.toString(AnalyticsManager.pingTimeout)
            + "&ka="
            + Long.toString(AnalyticsManager.pingTimeout)
            + "&ra=";

    verify(httpUrlConnectionProvider, timeout(AnalyticsManager.pingTimeout))
        .getHttpUrlConnection(ArgumentMatchers.startsWith(expectedUriString));
  }

  private List<String> capturedEventNames(ArgumentCaptor<TrackMessage.Builder> argCaptor) {
    return argCaptor
        .getAllValues()
        .stream()
        .map(mb -> mb.build().event())
        .collect(Collectors.toList());
  }

  private Builder isEvent(AnalyticsEvent event) {
    return argThat((TrackMessage.Builder mb) -> event.toString().equals(mb.build().event()));
  }
}
