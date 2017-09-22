/*
 * Copyright (c) 2016-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.che.keycloak.ide;

import static com.redhat.che.keycloak.shared.KeycloakConstants.DISABLED_SETTING;

import com.google.common.base.Preconditions;
import com.google.gwt.http.client.RequestBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.redhat.che.keycloak.shared.KeycloakConstants;
import java.util.List;
import org.eclipse.che.ide.MimeType;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.rest.AsyncRequest;
import org.eclipse.che.ide.rest.AsyncRequestFactory;
import org.eclipse.che.ide.rest.HTTPHeader;

/** KeycloakAuthAsyncRequestFactory */
@Singleton
public class KeycloakAsyncRequestFactory extends AsyncRequestFactory {
  private final DtoFactory dtoFactory;
  private AppContext appContext;
  private boolean keycloakDisabled = false;

  @Inject
  public KeycloakAsyncRequestFactory(DtoFactory dtoFactory, AppContext appContext) {
    super(dtoFactory);
    this.dtoFactory = dtoFactory;
    this.appContext = appContext;
    this.keycloakDisabled =
        isKeycloakDisabled(
            KeycloakConstants.getEndpoint(appContext.getMasterEndpoint()), DISABLED_SETTING);
  }

  @Override
  protected AsyncRequest doCreateRequest(
      RequestBuilder.Method method, String url, Object dtoBody, boolean async) {
    Preconditions.checkNotNull(method, "Request method should not be a null");

    if (keycloakDisabled) {
      return super.doCreateRequest(method, url, dtoBody, async);
    }

    AsyncRequest asyncRequest = new KeycloakAsyncRequest(method, url, async);
    if (dtoBody != null) {
      if (dtoBody instanceof List) {
        asyncRequest.data(dtoFactory.toJson((List) dtoBody));
      } else if (dtoBody instanceof String) {
        asyncRequest.data((String) dtoBody);
      } else {
        asyncRequest.data(dtoFactory.toJson(dtoBody));
      }
      asyncRequest.header(HTTPHeader.CONTENT_TYPE, MimeType.APPLICATION_JSON);
    } else if (method.equals(RequestBuilder.POST) || method.equals(RequestBuilder.PUT)) {

      /*
        Here we need to setup wildcard mime type in content-type header, because CORS filter
        responses with 403 error in case if user makes POST/PUT request with null body and without
        content-type header. Setting content-type header with wildcard mime type solves this problem.

        Note, this issue need to be investigated, because the problem may be occurred as a bug in
        CORS filter.
      */

      asyncRequest.header(HTTPHeader.CONTENT_TYPE, MimeType.WILDCARD);
    }
    asyncRequest.header(HTTPHeader.AUTHORIZATION, getBearerToken());
    return asyncRequest;
  }

  public static native String getBearerToken() /*-{
        //$wnd.keycloak.updateToken(10);
        return "Bearer " + $wnd.keycloak.token;
    }-*/;

  public static native void log(String message) /*-{
      console.log(message);
    }-*/;

  public static native boolean isKeycloakDisabled(
      String keycloakSettingsEndpoint, String disabledSetting) /*-{
      var myReq = new XMLHttpRequest();
      myReq.open('GET', '' + keycloakSettingsEndpoint, false);
      myReq.send(null);
      var keycloakDisabled = JSON.parse(myReq.responseText);
      if (keycloakDisabled['' + disabledSetting] != "true") {
        return false;
      } else {
        return true;
      }
    }-*/;
}
