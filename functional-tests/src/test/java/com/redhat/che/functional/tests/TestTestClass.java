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
package com.redhat.che.functional.tests;

import com.google.inject.Inject;
import javax.inject.Named;
import org.eclipse.che.selenium.core.user.DefaultTestUser;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.eclipse.che.selenium.pageobject.Menu;
import org.eclipse.che.selenium.pageobject.ProjectExplorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

public class TestTestClass {

  private static final Logger LOG = LoggerFactory.getLogger(TestTestClass.class);

  @Inject private TestWorkspace testWorkspace;
  @Inject private ProjectExplorer projectExplorer;
  @Inject private Menu menu;

  @Inject
  @Named("che.host")
  private String cheHost;

  @Inject private DefaultTestUser defaultTestUser;

  @Test
  public void dummyTestCase() {
    LOG.info("Test is running against:" + cheHost + " with:" + defaultTestUser.getName());
  }
}
