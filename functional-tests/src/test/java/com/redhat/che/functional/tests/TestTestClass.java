package com.redhat.che.functional.tests;

import com.google.inject.Inject;
import java.util.concurrent.ExecutionException;
import javax.annotation.PreDestroy;
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

  @Inject
  private TestWorkspace testWorkspace;
  @Inject
  private ProjectExplorer projectExplorer;
  @Inject
  private Menu menu;

  @Inject
  @Named("che.host")
  private String cheHost;
  @Inject
  private DefaultTestUser defaultTestUser;

  @Test
  public void dummyTestCase() {
    LOG.info("Test is running against:" + cheHost + " with:" + defaultTestUser.getName());
  }

}