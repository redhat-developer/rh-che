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
package com.redhat.che.selenium.core;

import com.google.inject.Injector;
import com.redhat.che.selenium.core.workspace.RhCheTestWorkspaceProvider;
import java.lang.reflect.Field;
import org.eclipse.che.selenium.core.workspace.AbstractTestWorkspaceInjector;
import org.eclipse.che.selenium.core.workspace.InjectTestWorkspace;
import org.eclipse.che.selenium.core.workspace.TestWorkspaceProvider;

public class RhCheTestWorkspaceInjector<T> extends AbstractTestWorkspaceInjector<T> {

  public RhCheTestWorkspaceInjector(
      Field field, InjectTestWorkspace injectTestWorkspace, Injector injector) {
    super(field, injectTestWorkspace, injector);
  }

  @Override
  protected TestWorkspaceProvider getTestWorkspaceProvider() {
    return injector.getInstance(RhCheTestWorkspaceProvider.class);
  }
}
