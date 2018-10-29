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

import static com.google.inject.matcher.Matchers.any;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.redhat.che.selenium.core.workspace.RhCheTestWorkspaceImpl;
import java.lang.reflect.Field;
import org.eclipse.che.selenium.core.workspace.InjectTestWorkspace;

public class RhCheSeleniumClassModule extends AbstractModule {
  @Override
  public void configure() {
    bindListener(any(), new RhCheWorkspaceTypeListener(binder().getProvider(Injector.class)));
  }

  private class RhCheWorkspaceTypeListener implements TypeListener {
    private final Provider<Injector> injectorProvider;

    public RhCheWorkspaceTypeListener(Provider<Injector> injector) {
      this.injectorProvider = injector;
    }

    @Override
    public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
      Class<?> clazz = type.getRawType();
      for (Field field : clazz.getDeclaredFields()) {
        if (field.getType() == RhCheTestWorkspaceImpl.class
            && field.isAnnotationPresent(InjectTestWorkspace.class)) {
          encounter.register(
              new RhCheTestWorkspaceInjector<>(
                  field, field.getAnnotation(InjectTestWorkspace.class), injectorProvider.get()));
        }
      }
    }
  }
}
