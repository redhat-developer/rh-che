/*
 * Copyright (c) 2018 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.che.multitenant.multicluster;

import com.google.common.collect.ImmutableMap;
import com.redhat.che.multitenant.UserBasedWorkspacesRoutingSuffixProvider;
import java.util.Map;

/**
 * Provides mapping between OpenShift cluster URL ('cluster-url' returned by osio
 * 'api/user/services' endpoint) and workspace routing suffix.
 *
 * <p>Currently info provided by `api/user/services` contains the following data:
 *
 * <p>"cluster-url": "https://api.starter-us-east-2.openshift.com", "created-at":
 * "2017-04-21T14:14:35.536721Z", "name": "user-che", "state": "created", "type": "che",
 * "updated-at": "2017-04-21T14:14:35.536721Z", "version": "1.0.93"
 *
 * <p>Should be deprecated and removed once 'api/user/services' would provide info about routing
 * suffix
 *
 * @see <a href="https://github.com/fabric8-services/fabric8-wit/issues/1859">fabric8-wit issue</a>
 * @see UserBasedWorkspacesRoutingSuffixProvider
 * @author Ilya Buziuk <ibuziuk@redhat.com>
 */
public final class ClusterToRoutingSuffixMapping {

  private static final Map<String, String> CLUSTER_ROUTING_SUFFIX_MAPPING =
      ImmutableMap.<String, String>builder()
          .put("https://api.free-stg.openshift.com", "1b7d.free-stg.openshiftapps.com")
          .put(
              "https://api.starter-us-east-2.openshift.com",
              "8a09.starter-us-east-2.openshiftapps.com")
          .build();

  private ClusterToRoutingSuffixMapping() {}

  public static Map<String, String> get() {
    return CLUSTER_ROUTING_SUFFIX_MAPPING;
  }
}
