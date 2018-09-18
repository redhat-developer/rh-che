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
package com.redhat.che.plugin.analytics.wsmaster;

import com.google.common.annotations.Beta;
import java.util.Map;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.eclipse.che.api.factory.shared.dto.FactoryDto;

/**
 * This interceptor must be bound for the method {@link
 * FactoryParametersResolver#createFactory(Map<String, String>)}
 *
 * @author David Festal
 */
@Beta
public class FactoryUrlSetterInterceptor implements MethodInterceptor {

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    final Object[] args = invocation.getArguments();
    @SuppressWarnings("unchecked")
    final Map<String, String> parameters = (Map<String, String>) args[0];
    FactoryDto factory = (FactoryDto) invocation.proceed();
    Map<String, String> attributes = factory.getWorkspace().getAttributes();
    parameters.forEach(
        (k, v) -> {
          attributes.put("factory.parameter." + k, v);
        });
    return factory;
  }
}
